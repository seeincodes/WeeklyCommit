package com.acme.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedPrincipalTest {

  private static final UUID EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

  @Test
  void of_withAllClaims_extractsEverything() {
    JwtAuthenticationToken auth =
        authFromClaims(
            Map.of(
                "sub", EMPLOYEE.toString(),
                "org_id", ORG.toString(),
                "manager_id", MANAGER.toString(),
                "timezone", "America/Los_Angeles",
                "roles", List.of("MANAGER")),
            List.of("ROLE_MANAGER"));

    AuthenticatedPrincipal p = AuthenticatedPrincipal.of(auth);

    assertThat(p.employeeId()).isEqualTo(EMPLOYEE);
    assertThat(p.organizationId()).isEqualTo(ORG);
    assertThat(p.managerId()).contains(MANAGER);
    assertThat(p.timezone()).isEqualTo(ZoneId.of("America/Los_Angeles"));
    assertThat(p.roles()).containsExactly("MANAGER");
    assertThat(p.isManager()).isTrue();
    assertThat(p.hasRole("MANAGER")).isTrue();
    assertThat(p.hasRole("ADMIN")).isFalse();
  }

  @Test
  void of_missingManagerId_yieldsEmptyOptional() {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", EMPLOYEE.toString());
    claims.put("org_id", ORG.toString());
    AuthenticatedPrincipal p = AuthenticatedPrincipal.of(authFromClaims(claims, List.of()));
    assertThat(p.managerId()).isEmpty();
  }

  @Test
  void of_malformedManagerId_yieldsEmptyOptional() {
    // Defensive: admin report surfaces these rather than crashing.
    AuthenticatedPrincipal p =
        AuthenticatedPrincipal.of(
            authFromClaims(
                Map.of(
                    "sub", EMPLOYEE.toString(),
                    "org_id", ORG.toString(),
                    "manager_id", "not-a-uuid"),
                List.of()));
    assertThat(p.managerId()).isEmpty();
  }

  @Test
  void of_missingSub_throws() {
    HashMap<String, Object> claims = new HashMap<>();
    claims.put("org_id", ORG.toString());
    assertThatThrownBy(() -> AuthenticatedPrincipal.of(authFromClaims(claims, List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sub");
  }

  @Test
  void of_missingOrgId_throws() {
    HashMap<String, Object> claims = new HashMap<>();
    claims.put("sub", EMPLOYEE.toString());
    assertThatThrownBy(() -> AuthenticatedPrincipal.of(authFromClaims(claims, List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("org_id");
  }

  @Test
  void of_malformedSub_throws() {
    assertThatThrownBy(
            () ->
                AuthenticatedPrincipal.of(
                    authFromClaims(
                        Map.of("sub", "not-a-uuid", "org_id", ORG.toString()), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sub");
  }

  @Test
  void of_missingTimezone_fallsBackToUtc() {
    AuthenticatedPrincipal p =
        AuthenticatedPrincipal.of(
            authFromClaims(
                Map.of("sub", EMPLOYEE.toString(), "org_id", ORG.toString()), List.of()));
    assertThat(p.timezone()).isEqualTo(ZoneId.of("UTC"));
  }

  @Test
  void of_invalidTimezone_fallsBackToUtc() {
    // Auth0 profile could carry a malformed IANA id; don't crash the request.
    AuthenticatedPrincipal p =
        AuthenticatedPrincipal.of(
            authFromClaims(
                Map.of(
                    "sub", EMPLOYEE.toString(),
                    "org_id", ORG.toString(),
                    "timezone", "Not/A/Real/Zone"),
                List.of()));
    assertThat(p.timezone()).isEqualTo(ZoneId.of("UTC"));
  }

  @Test
  void of_rolesPrefixStripped() {
    // SecurityConfig prefixes ROLE_; AuthenticatedPrincipal strips it for consumer convenience.
    AuthenticatedPrincipal p =
        AuthenticatedPrincipal.of(
            authFromClaims(
                Map.of(
                    "sub", EMPLOYEE.toString(),
                    "org_id", ORG.toString()),
                List.of("ROLE_MANAGER", "ROLE_ADMIN")));
    assertThat(p.roles()).containsExactlyInAnyOrder("MANAGER", "ADMIN");
  }

  @Test
  void of_withNonJwtAuthentication_throws() {
    TestingAuthenticationToken other = new TestingAuthenticationToken("user", "pwd");
    assertThatThrownBy(() -> AuthenticatedPrincipal.of(other))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JwtAuthenticationToken");
  }

  private static JwtAuthenticationToken authFromClaims(
      Map<String, Object> claims, List<String> authorities) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            claims);
    return new JwtAuthenticationToken(
        jwt, authorities.stream().map(SimpleGrantedAuthority::new).toList());
  }
}
