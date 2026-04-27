package com.acme.weeklycommit.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Demo-only security override that opens up the in-process {@link StubRcdoController} to
 * unauthenticated calls. The backend's own {@link
 * com.acme.weeklycommit.integration.rcdo.RcdoClient} hits {@code http://localhost:8080/rcdo/...}
 * with a service-token bearer header, not a JWT -- the default security chain rejects that as
 * unauthenticated. Allowing the path lets the stub round-trip work without a real Auth0 tenant.
 *
 * <p>Profile-gated to {@code demo} only. Production has no {@code /rcdo} endpoint of its own (the
 * integration calls hit the real RCDO host externally), so this filter chain never matches outside
 * the demo deploy.
 *
 * <p>Order is set to {@link Ordered#HIGHEST_PRECEDENCE} so the path-scoped chain runs before the
 * default {@code SecurityConfig} chain. Spring picks the first chain whose {@code securityMatcher}
 * matches the request -- only requests starting with {@code /rcdo/} land here; everything else
 * falls through to the default authenticated chain.
 */
@Configuration
@Profile("demo")
public class DemoSecurityConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain demoRcdoStubFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/rcdo/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
