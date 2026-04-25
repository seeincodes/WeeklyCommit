package com.acme.weeklycommit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JobMetrics}. Uses a real {@link SimpleMeterRegistry} (no mocks) -- the
 * helper has zero IO/DB dependencies, so a real registry is the most direct way to assert that
 * counters and timers are registered and labeled correctly.
 */
class JobMetricsTest {

  @Test
  void timed_success_incrementsSuccessCounter_recordsDuration() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JobMetrics metrics = new JobMetrics(registry);

    metrics.timed("AutoLockJob", () -> {});

    double successCount =
        registry
            .get("weekly_commit.scheduled.job.runs_total")
            .tag("job", "AutoLockJob")
            .tag("outcome", "success")
            .counter()
            .count();
    assertThat(successCount).isEqualTo(1.0);

    Timer successTimer =
        registry
            .get("weekly_commit.scheduled.job.duration_seconds")
            .tag("job", "AutoLockJob")
            .tag("outcome", "success")
            .timer();
    assertThat(successTimer.count()).isEqualTo(1L);
  }

  @Test
  void timed_runtimeException_incrementsFailureCounter_rethrows() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JobMetrics metrics = new JobMetrics(registry);

    RuntimeException boom = new RuntimeException("simulated job-level failure");
    assertThatThrownBy(
            () ->
                metrics.timed(
                    "ArchivalJob",
                    () -> {
                      throw boom;
                    }))
        .isSameAs(boom);

    double failureCount =
        registry
            .get("weekly_commit.scheduled.job.runs_total")
            .tag("job", "ArchivalJob")
            .tag("outcome", "failure")
            .counter()
            .count();
    assertThat(failureCount).isEqualTo(1.0);

    Timer failureTimer =
        registry
            .get("weekly_commit.scheduled.job.duration_seconds")
            .tag("job", "ArchivalJob")
            .tag("outcome", "failure")
            .timer();
    assertThat(failureTimer.count()).isEqualTo(1L);
  }

  @Test
  void timed_durationLabelsMatchOutcome() {
    // A success run must NOT pollute the failure timer (and vice versa). This guards against a
    // refactor where someone records into the same Timer.Sample stop target regardless of outcome.
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JobMetrics metrics = new JobMetrics(registry);

    metrics.timed("UnreviewedDigestJob", () -> {});
    assertThatThrownBy(
            () ->
                metrics.timed(
                    "UnreviewedDigestJob",
                    () -> {
                      throw new RuntimeException("x");
                    }))
        .isInstanceOf(RuntimeException.class);

    Timer successTimer =
        registry
            .get("weekly_commit.scheduled.job.duration_seconds")
            .tag("job", "UnreviewedDigestJob")
            .tag("outcome", "success")
            .timer();
    Timer failureTimer =
        registry
            .get("weekly_commit.scheduled.job.duration_seconds")
            .tag("job", "UnreviewedDigestJob")
            .tag("outcome", "failure")
            .timer();
    assertThat(successTimer.count()).isEqualTo(1L);
    assertThat(failureTimer.count()).isEqualTo(1L);
  }
}
