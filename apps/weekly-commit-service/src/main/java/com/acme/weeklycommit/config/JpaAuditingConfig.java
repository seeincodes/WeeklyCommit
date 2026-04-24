package com.acme.weeklycommit.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Resolves the current auditor for {@code @CreatedBy} / {@code @LastModifiedBy}.
 *
 * <p>Pulls the employee UUID from the JWT {@code sub} claim. Scheduled jobs and startup code with
 * no SecurityContext record {@code "system"} — this is the correct value, not a placeholder.
 *
 * <p>{@code @EnableJpaAuditing} lives here (rather than on the main application class) so
 * {@code @WebMvcTest} slices do not pull JPA auto-config in. Integration tests that want auditing
 * opt in via {@code @Import(JpaAuditingConfig.class)} (see {@code @JpaTestSlice}).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

  static final String SYSTEM_AUDITOR = "system";

  @Bean
  AuditorAware<String> auditorAware() {
    return () -> Optional.of(resolveAuditor());
  }

  private static String resolveAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
      String sub = jwt.getToken().getSubject();
      return (sub == null || sub.isBlank()) ? SYSTEM_AUDITOR : sub;
    }
    return SYSTEM_AUDITOR;
  }
}
