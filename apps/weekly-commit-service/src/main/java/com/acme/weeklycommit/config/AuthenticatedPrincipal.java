package com.acme.weeklycommit.config;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * View over a validated Auth0 JWT. Exposes the fields our code actually needs: {@code sub}, {@code
 * org_id}, nullable {@code manager_id}, {@code roles}, {@code timezone}. Pulled from the {@link
 * Authentication} in the SecurityContext.
 *
 * <p>Callers grab it via {@link #current()}; controllers that receive {@code Authentication} as a
 * method parameter can use {@link #of(Authentication)}. A dedicated argument resolver belongs in
 * group 6 where controllers are built.
 */
public record AuthenticatedPrincipal(
    UUID employeeId,
    UUID organizationId,
    Optional<UUID> managerId,
    Set<String> roles,
    ZoneId timezone,
    Jwt jwt) {

  /**
   * Compact constructor — defensively copies {@code roles} into an immutable {@link Set}. Gives
   * the record genuine value-object semantics: once constructed, mutation attempts on the
   * returned Set throw {@code UnsupportedOperationException}, so neither callers nor later code
   * can alter the authorization view of this principal.
   */
  public AuthenticatedPrincipal {
    roles = Set.copyOf(roles);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean isManager() {
    return hasRole("MANAGER");
  }

  public static AuthenticatedPrincipal of(Authentication auth) {
    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
      throw new IllegalStateException(
          "Expected JwtAuthenticationToken, got " + auth.getClass().getName());
    }
    Jwt jwt = jwtAuth.getToken();
    UUID employeeId = requireUuid(jwt, "sub");
    UUID orgId = requireUuid(jwt, "org_id");
    Optional<UUID> managerId = optionalUuid(jwt, "manager_id");
    Set<String> roles = extractRoles(jwtAuth);
    ZoneId tz = extractTimezone(jwt);
    return new AuthenticatedPrincipal(employeeId, orgId, managerId, roles, tz, jwt);
  }

  private static UUID requireUuid(Jwt jwt, String claim) {
    String raw = jwt.getClaimAsString(claim);
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("JWT missing required claim: " + claim);
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("JWT claim '" + claim + "' is not a UUID", e);
    }
  }

  private static Optional<UUID> optionalUuid(Jwt jwt, String claim) {
    String raw = jwt.getClaimAsString(claim);
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      // Treat malformed manager_id as absent; surfaces in admin unassigned report (group 6).
      return Optional.empty();
    }
  }

  private static Set<String> extractRoles(JwtAuthenticationToken token) {
    return token.getAuthorities().stream()
        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
        .map(s -> s.startsWith("ROLE_") ? s.substring(5) : s)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static ZoneId extractTimezone(Jwt jwt) {
    String tz = jwt.getClaimAsString("timezone");
    if (tz == null || tz.isBlank()) {
      return ZoneId.of("UTC");
    }
    try {
      return ZoneId.of(tz);
    } catch (Exception e) {
      return ZoneId.of("UTC");
    }
  }
}
