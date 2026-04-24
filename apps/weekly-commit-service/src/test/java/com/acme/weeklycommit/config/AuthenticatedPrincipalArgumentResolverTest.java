package com.acme.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedPrincipalArgumentResolverTest {

  private final AuthenticatedPrincipalArgumentResolver resolver =
      new AuthenticatedPrincipalArgumentResolver();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void supportsParameter_returnsTrue_forAuthenticatedPrincipalType() throws NoSuchMethodException {
    MethodParameter p =
        new MethodParameter(Fixtures.class.getDeclaredMethod("takesPrincipal", AuthenticatedPrincipal.class), 0);
    assertThat(resolver.supportsParameter(p)).isTrue();
  }

  @Test
  void supportsParameter_returnsFalse_forOtherTypes() throws NoSuchMethodException {
    MethodParameter p =
        new MethodParameter(Fixtures.class.getDeclaredMethod("takesString", String.class), 0);
    assertThat(resolver.supportsParameter(p)).isFalse();
  }

  @Test
  void resolve_withJwtAuth_returnsPopulatedPrincipal() throws Exception {
    UUID employeeId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    JwtAuthenticationToken auth =
        jwtAuth(
            Map.of("sub", employeeId.toString(), "org_id", orgId.toString()),
            List.of("ROLE_MANAGER"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    AuthenticatedPrincipal resolved =
        (AuthenticatedPrincipal) resolver.resolveArgument(null, null, null, null);

    assertThat(resolved.employeeId()).isEqualTo(employeeId);
    assertThat(resolved.organizationId()).isEqualTo(orgId);
    assertThat(resolved.isManager()).isTrue();
  }

  @Test
  void resolve_withoutAuth_throwsIllegalState() {
    // If Spring Security let the request through without authentication, the controller
    // must not be given a partially-constructed principal. Fail loudly.
    SecurityContextHolder.clearContext();

    assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authenticated");
  }

  @Test
  void resolve_withNonJwtAuth_throwsIllegalState() {
    // Defensive: only OAuth2 JWT is acceptable. Any other Authentication type is a bug.
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p"));

    assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- fixtures ---

  private static JwtAuthenticationToken jwtAuth(Map<String, Object> claims, List<String> roles) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            claims);
    return new JwtAuthenticationToken(
        jwt, roles.stream().map(SimpleGrantedAuthority::new).toList());
  }

  @SuppressWarnings("unused")
  private static class Fixtures {
    void takesPrincipal(AuthenticatedPrincipal p) {}

    void takesString(String s) {}
  }
}
