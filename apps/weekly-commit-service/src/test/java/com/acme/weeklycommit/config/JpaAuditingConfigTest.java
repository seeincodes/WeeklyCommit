package com.acme.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JpaAuditingConfigTest {

  private final AuditorAware<String> auditorAware = new JpaAuditingConfig().auditorAware();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void noSecurityContext_returnsSystem() {
    assertThat(auditorAware.getCurrentAuditor()).isEqualTo(Optional.of("system"));
  }

  @Test
  void authenticatedJwt_returnsSubjectClaim() {
    String employeeId = "00000000-0000-0000-0000-0000000000a1";
    JwtAuthenticationToken token =
        new JwtAuthenticationToken(
            new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("sub", employeeId)),
            java.util.List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
    SecurityContextHolder.getContext().setAuthentication(token);

    assertThat(auditorAware.getCurrentAuditor()).isEqualTo(Optional.of(employeeId));
  }

  @Test
  void jwtWithBlankSubject_fallsBackToSystem() {
    JwtAuthenticationToken token =
        new JwtAuthenticationToken(
            new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("sub", "")),
            java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(token);

    assertThat(auditorAware.getCurrentAuditor()).isEqualTo(Optional.of("system"));
  }

  @Test
  void nonJwtAuthentication_returnsSystem() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "pw"));
    assertThat(auditorAware.getCurrentAuditor()).isEqualTo(Optional.of("system"));
  }

  @Test
  void anonymousAuthentication_returnsSystem() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertThat(auditorAware.getCurrentAuditor()).isEqualTo(Optional.of("system"));
  }
}
