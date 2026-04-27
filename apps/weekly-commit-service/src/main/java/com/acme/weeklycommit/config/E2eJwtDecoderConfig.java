package com.acme.weeklycommit.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * E2E-profile {@link JwtDecoder} that trusts JWTs signed with the test RSA private key whose public
 * half lives on the classpath at {@code e2e-keys/public-key.pem}. Used by the cross-remote Cypress
 * + Cucumber suite (TASK_LIST group 13) so test scenarios can authenticate without needing an Auth0
 * tenant.
 *
 * <p>Profile-gated to {@code "e2e"} -- production runs the {@code prod} profile (or no profile) and
 * reaches the Spring Boot auto-configured {@code NimbusJwtDecoder} that validates against the Auth0
 * JWKS endpoint. The integration test {@code E2eJwtDecoderConfigIT} pins this contract.
 *
 * <p>Why a static public key instead of a JWKS URI:
 *
 * <ul>
 *   <li>No network during tests -- faster, deterministic, no flaky DNS in CI.
 *   <li>One file to keep in sync, not a JWKS server stub.
 *   <li>NimbusJwtDecoder.withPublicKey is the documented Spring Security pattern for this case.
 * </ul>
 *
 * <p>The matching private key lives in {@code
 * apps/weekly-commit-ui/cypress/support/auth/keys/private-key.pem} and is committed (not a
 * production secret -- see the README in either keys directory for why).
 */
@Configuration
@Profile("e2e")
public class E2eJwtDecoderConfig {

  private static final String PUBLIC_KEY_RESOURCE = "e2e-keys/public-key.pem";

  /**
   * Replaces the auto-configured {@link JwtDecoder} from {@link
   * OAuth2ResourceServerAutoConfiguration} when the e2e profile is active. Spring's
   * {@code @ConditionalOnMissingBean} on the auto-config means this bean wins.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    try {
      return NimbusJwtDecoder.withPublicKey(loadPublicKey()).build();
    } catch (Exception e) {
      throw new IllegalStateException(
          "E2E profile active but failed to load test public key from " + PUBLIC_KEY_RESOURCE, e);
    }
  }

  private static RSAPublicKey loadPublicKey()
      throws IOException, java.security.GeneralSecurityException {
    ClassPathResource resource = new ClassPathResource(PUBLIC_KEY_RESOURCE);
    String pem;
    try (InputStream in = resource.getInputStream()) {
      pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    String base64 =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
    PublicKey key = KeyFactory.getInstance("RSA").generatePublic(spec);
    return (RSAPublicKey) key;
  }
}
