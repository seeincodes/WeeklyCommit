package com.acme.weeklycommit.scheduled;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires Shedlock so scheduled jobs run exactly once across pods. The {@link
 * JdbcTemplateLockProvider} reads/writes the {@code shedlock} table created in V8.
 *
 * <p>{@link EnableSchedulerLock} sets the default lock TTL: a job that takes longer than the
 * default-lock-at-most-for value can be re-acquired by another pod (the assumption being that the
 * original holder crashed). Per-job overrides can be set via {@code @SchedulerLock(lockAtMostFor=
 * "...")} on each method. We default to a generous 10 minutes so the typical few-second jobs
 * release fast while a wedged pod can't hog the lock forever.
 *
 * <p>Gated by {@code SHEDLOCK_ENABLED} (default {@code true}). Set to {@code false} in test
 * profiles where scheduling is unwanted (e.g. unit tests of business logic that import this config
 * indirectly via {@code @SpringBootTest}).
 */
@Configuration
@ConditionalOnProperty(name = "shedlock.enabled", havingValue = "true", matchIfMissing = true)
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulingConfig {

  @Bean
  LockProvider lockProvider(DataSource dataSource, JdbcTemplate jdbcTemplate) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(jdbcTemplate)
            .usingDbTime() // server-side TIMESTAMPTZ comparison; avoids JVM-clock skew across pods
            .build());
  }
}
