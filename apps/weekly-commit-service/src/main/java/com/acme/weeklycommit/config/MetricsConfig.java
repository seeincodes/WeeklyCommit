package com.acme.weeklycommit.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Micrometer {@code @Timed} aspect into Spring's AOP infrastructure.
 *
 * <p>Without this bean {@code @Timed} compiles fine but fires nothing at runtime. Spring Boot's
 * {@code spring-boot-starter-actuator} brings in {@code micrometer-core} (where {@code @Timed} and
 * {@link TimedAspect} live), and {@code spring-boot-starter-aop} brings the proxy machinery — but
 * neither auto-registers the aspect bean. We do it explicitly here so the timer histograms show up
 * at {@code /actuator/prometheus} for the four API endpoints PRD §Performance Targets calls out:
 *
 * <ul>
 *   <li>{@code GET /api/v1/plans/me/current} — IC common path, p95 &lt; 200 ms target
 *   <li>{@code GET /api/v1/plans} — manager lookup-by-employee, same hot path
 *   <li>{@code GET /api/v1/plans/team} — paginated team list, used by the team route
 *   <li>{@code GET /api/v1/rollup/team} — aggregated dashboard, p95 &lt; 500 ms target
 * </ul>
 *
 * <p>The k6 perf harness ({@code apps/weekly-commit-ui/perf/}) gives us black-box latency from the
 * caller's perspective; these in-process histograms surface the server-side breakdown so a
 * regression has both an external and internal signal.
 */
@Configuration
public class MetricsConfig {

  @Bean
  TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }
}
