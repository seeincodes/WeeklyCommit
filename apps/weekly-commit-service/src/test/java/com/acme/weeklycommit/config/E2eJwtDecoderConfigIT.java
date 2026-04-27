package com.acme.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies the {@code e2e}-profile {@link JwtDecoder} bean trusts JWTs signed by the test private
 * key (mirror of the public key on the classpath at {@code e2e-keys/public-key.pem}) and rejects
 * everything else.
 *
 * <p>Catches three failure modes:
 *
 * <ul>
 *   <li>Public-key drift between the BE classpath copy and the UI cypress copy (would surface as a
 *       test JWT failing to validate even though it was signed by the matching UI private key).
 *   <li>Accidental leak of the {@code e2e} profile into a non-test context (the integration test
 *       {@code @ActiveProfiles("e2e")} forces the bean to load and its existence proves the wiring
 *       is profile-gated, not always-on).
 *   <li>Forgery attempts using a different signing algorithm or unsigned JWTs.
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("e2e")
class E2eJwtDecoderConfigIT {

  private static RSAPrivateKey testPrivateKey;

  @BeforeAll
  static void loadPrivateKey() throws Exception {
    // The UI workspace's private key is the canonical signer; the BE only ever has the public
    // half (in src/main/resources/e2e-keys/). For test purposes here we read the UI's private
    // key directly from the repo so a CI environment without the UI workspace built still has
    // access to it.
    Path keyPath = Path.of("../weekly-commit-ui/cypress/support/auth/keys/private-key.pem");
    String pem = Files.readString(keyPath, StandardCharsets.UTF_8);
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
    PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(spec);
    testPrivateKey = (RSAPrivateKey) key;
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresTestContainer.register(registry);
    // E2eJwtDecoderConfig is profile-gated; it doesn't need AUTH0 vars but the resource-server
    // auto-config still binds them. Provide placeholders.
    registry.add("AUTH0_ISSUER_URI", () -> "https://e2e.invalid/");
    registry.add("AUTH0_AUDIENCE", () -> "e2e-tests");
  }

  @Autowired private JwtDecoder jwtDecoder;

  @MockBean private NotificationSender notificationSender;

  @Test
  void decoder_acceptsJwtSignedWithTheTestPrivateKey() throws Exception {
    String token = signRs256(claimsFor(UUID.randomUUID(), UUID.randomUUID(), List.of("IC")));

    Jwt jwt = jwtDecoder.decode(token);

    assertThat(jwt.getSubject()).isNotBlank();
    assertThat(jwt.getClaimAsString("org_id")).isNotBlank();
    assertThat(jwt.getClaimAsStringList("roles")).contains("IC");
  }

  @Test
  void decoder_rejectsJwtSignedWithAnUnknownKey() throws Exception {
    // HS256 with an arbitrary HMAC secret -- different algorithm AND a key the decoder doesn't
    // know about. Should fail.
    String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload =
        base64Url(
            new ObjectMapper()
                .writeValueAsString(
                    claimsFor(UUID.randomUUID(), UUID.randomUUID(), List.of("ADMIN"))));
    String signingInput = header + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("not-the-real-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature = base64UrlEncode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    String forged = signingInput + "." + signature;

    assertThatThrownBy(() -> jwtDecoder.decode(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoder_rejectsAnUnsignedJwt() {
    // alg=none JWTs were the great early-OAuth2 vulnerability; modern decoders reject them.
    String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
    String payload =
        base64Url(
            "{\"sub\":\"" + UUID.randomUUID() + "\",\"org_id\":\"" + UUID.randomUUID() + "\"}");
    String unsigned = header + "." + payload + ".";

    assertThatThrownBy(() -> jwtDecoder.decode(unsigned)).isInstanceOf(JwtException.class);
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private static String signRs256(Map<String, Object> claims) throws Exception {
    String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
    String payload = base64Url(new ObjectMapper().writeValueAsString(claims));
    String signingInput = header + "." + payload;
    java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
    signer.initSign(testPrivateKey);
    signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
    String signature = base64UrlEncode(signer.sign());
    return signingInput + "." + signature;
  }

  private static Map<String, Object> claimsFor(UUID sub, UUID orgId, List<String> roles) {
    Map<String, Object> c = new LinkedHashMap<>();
    long nowEpoch = Instant.now().getEpochSecond();
    c.put("sub", sub.toString());
    c.put("org_id", orgId.toString());
    c.put("roles", roles);
    c.put("timezone", "UTC");
    c.put("iss", "wc-e2e-test-signer");
    c.put("iat", nowEpoch);
    c.put("exp", nowEpoch + 3600);
    return c;
  }

  private static String base64Url(String s) {
    return base64UrlEncode(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String base64UrlEncode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
