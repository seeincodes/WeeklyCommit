package com.acme.weeklycommit.scheduled;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Wraps a scheduled job's body to publish two Micrometer metric families per run:
 *
 * <ul>
 *   <li>{@code weekly_commit.scheduled.job.runs_total{job=<name>,outcome=success|failure}} -- a
 *       counter incremented once per scheduled tick. SREs alarm on absence (job stopped firing) and
 *       on a 100% failure rate.
 *   <li>{@code weekly_commit.scheduled.job.duration_seconds{job=<name>,outcome=success|failure}} --
 *       a timer recording how long the wrapped body took. Useful for spotting jobs that
 *       progressively slow as the data set grows.
 * </ul>
 *
 * <p>Picked up automatically by the Micrometer CloudWatch registry configured in group 7. The
 * {@code outcome=failure} label fires when the wrapped body throws -- per-item failures inside the
 * batch are already logged WARN by each job and are NOT job-level failures.
 *
 * <p>Wrapping lives on the {@code @Scheduled run()} method, not on {@code runOnce()}, so unit tests
 * of business logic can keep calling {@code runOnce()} without dragging metric assertions into
 * every test.
 */
@Component
public class JobMetrics {

  private static final String COUNTER_NAME = "weekly_commit.scheduled.job.runs_total";
  private static final String TIMER_NAME = "weekly_commit.scheduled.job.duration_seconds";

  private final MeterRegistry registry;

  public JobMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Run {@code body} and record its outcome + duration. If the body throws a {@link
   * RuntimeException}, the failure counter/timer are recorded and the exception is rethrown so the
   * Spring scheduler's own error handler still observes it.
   */
  public void timed(String jobName, Runnable body) {
    Timer.Sample sample = Timer.start(registry);
    try {
      body.run();
      sample.stop(timer(jobName, "success"));
      registry.counter(COUNTER_NAME, "job", jobName, "outcome", "success").increment();
    } catch (RuntimeException e) {
      sample.stop(timer(jobName, "failure"));
      registry.counter(COUNTER_NAME, "job", jobName, "outcome", "failure").increment();
      throw e;
    }
  }

  private Timer timer(String jobName, String outcome) {
    return Timer.builder(TIMER_NAME)
        .description("Duration of one scheduled-job run, tagged by outcome")
        .tag("job", jobName)
        .tag("outcome", outcome)
        .register(registry);
  }
}
