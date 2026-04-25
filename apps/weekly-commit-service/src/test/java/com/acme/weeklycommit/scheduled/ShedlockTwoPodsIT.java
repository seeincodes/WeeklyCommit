package com.acme.weeklycommit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies that two pods sharing a database serialize on the {@code shedlock} table. Constructs two
 * {@link JdbcTemplateLockProvider} instances pointed at the same DB (the production "two-pod"
 * topology) and asserts that one acquire succeeds and a concurrent acquire on the same lock name
 * returns {@link Optional#empty()}.
 *
 * <p>This is the closest we can get to a true multi-pod test inside one JVM. The full path (Spring
 * proxy + {@code @Scheduled} + {@code @SchedulerLock}) is exercised by the unit tests of each job;
 * serialization correctness is the unique behavior worth testing here.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShedlockTwoPodsIT {

  private static final String LOCK_NAME = "shedlock-two-pods-it";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void clearLocks() {
    jdbcTemplate.update("DELETE FROM shedlock WHERE name = ?", LOCK_NAME);
  }

  private LockProvider providerForPod() {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(jdbcTemplate)
            .usingDbTime()
            .build());
  }

  @Test
  void twoPodsContendOnSameLock_onlyOneAcquires() {
    LockProvider podA = providerForPod();
    LockProvider podB = providerForPod();
    LockConfiguration config =
        new LockConfiguration(
            Instant.now(), LOCK_NAME, Duration.ofMinutes(5), Duration.ofSeconds(0));

    Optional<SimpleLock> aLock = podA.lock(config);
    Optional<SimpleLock> bLock = podB.lock(config);

    assertThat(aLock).as("pod A should acquire first").isPresent();
    assertThat(bLock).as("pod B should be blocked while A holds").isEmpty();

    aLock.get().unlock();

    // After A releases, B can re-attempt and succeed.
    Optional<SimpleLock> bRetry = podB.lock(config);
    assertThat(bRetry).as("pod B re-acquires after A releases").isPresent();
    bRetry.get().unlock();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresTestContainer.register(registry);
    registry.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    registry.add("AUTH0_AUDIENCE", () -> "test-audience");
  }
}
