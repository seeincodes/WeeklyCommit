package com.acme.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

  @Test
  void noRolesClaim_yieldsEmptyAuthorities() {
    Jwt jwt = jwt(Map.of("sub", "emp-1"));
    assertThat(SecurityConfig.extractAuthorities(jwt)).isEmpty();
  }

  @Test
  void emptyRolesClaim_yieldsEmptyAuthorities() {
    Jwt jwt = jwt(Map.of("sub", "emp-1", "roles", List.of()));
    assertThat(SecurityConfig.extractAuthorities(jwt)).isEmpty();
  }

  @Test
  void rolesClaim_prefixedWithROLE() {
    Jwt jwt = jwt(Map.of("sub", "emp-1", "roles", List.of("MANAGER", "ADMIN")));
    assertThat(names(SecurityConfig.extractAuthorities(jwt)))
        .containsExactlyInAnyOrder("ROLE_MANAGER", "ROLE_ADMIN");
  }

  @Test
  void rolesAlreadyPrefixed_notDoublePrefixed() {
    Jwt jwt = jwt(Map.of("sub", "emp-1", "roles", List.of("ROLE_MANAGER")));
    assertThat(names(SecurityConfig.extractAuthorities(jwt))).containsExactly("ROLE_MANAGER");
  }

  private static Set<String> names(java.util.Collection<GrantedAuthority> auths) {
    return auths.stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Jwt jwt(Map<String, Object> claims) {
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
  }
}
