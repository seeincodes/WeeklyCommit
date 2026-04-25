package com.acme.weeklycommit.config;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auth0 OAuth2 resource-server wiring.
 *
 * <p>Issuer URI and expected audience come from environment (see {@code application.yml}). Spring
 * validates signature + issuer + audience automatically once {@code issuer-uri} + {@code audiences}
 * are set.
 *
 * <p>JWT claims we care about are extracted in {@link AuthenticatedPrincipal}. This class only maps
 * the {@code roles} claim into Spring authorities so {@code @PreAuthorize("hasRole('MANAGER')")}
 * works unchanged.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    // Admin surface (DLT replay, unassigned-employees report) requires ADMIN role
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    // Manager-scoped endpoints enforced with @PreAuthorize at the controller
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  /**
   * Extracts the {@code roles} claim (array of strings) into Spring {@code ROLE_*} authorities.
   *
   * <p>If the claim is missing, the principal gets no roles — manager endpoints return 403. That's
   * the correct failure mode; surfaces as a setup error immediately rather than accidental grant.
   */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractAuthorities);
    converter.setPrincipalClaimName("sub");
    return converter;
  }

  static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles == null || roles.isEmpty()) {
      return Set.of();
    }
    return roles.stream()
        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
        .collect(Collectors.toUnmodifiableSet());
  }
}
