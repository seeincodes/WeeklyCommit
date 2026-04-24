package com.acme.weeklycommit.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wall-clock bean for services that need to know "now" without hardcoding {@code Instant.now()}.
 * Fixed to UTC — MEMO: "Week math always UTC at service layer."
 *
 * <p>Tests replace this with {@link Clock#fixed(java.time.Instant, java.time.ZoneId)} via
 * constructor injection.
 */
@Configuration
public class ClockConfig {

  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemUTC().withZone(ZoneOffset.UTC);
  }
}
