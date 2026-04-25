package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.repo.NotificationDltRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes the {@code weekly_commit_notification_dlt_recent_count} gauge: count of DLT rows
 * created in the last hour. CloudWatch alarms on this gauge per PRD [MVP14] ("CloudWatch alarm on
 * any DLT row &lt; 1h old").
 *
 * <p>Refreshed every 60s by a {@link Scheduled} task. The gauge holds the most recent reading; a
 * brief stale window is acceptable since the alarm threshold is "any row in the last hour" --
 * sub-minute precision adds nothing.
 */
@Component
class DltMetricsPublisher {

  private static final Duration WINDOW = Duration.ofHours(1);
  private static final String METRIC_NAME = "weekly_commit.notification.dlt.recent_count";

  private final NotificationDltRepository dltRepo;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final AtomicLong currentCount = new AtomicLong(0);

  DltMetricsPublisher(NotificationDltRepository dltRepo, MeterRegistry meterRegistry, Clock clock) {
    this.dltRepo = dltRepo;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  @PostConstruct
  void register() {
    Gauge.builder(METRIC_NAME, currentCount, AtomicLong::get)
        .description("DLT rows created within the last hour (window=1h)")
        .baseUnit("rows")
        .register(meterRegistry);
    refresh();
  }

  /** Refresh every 60s. Cheap query (count over an indexed range). */
  @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
  void refresh() {
    long count = dltRepo.countCreatedSince(clock.instant().minus(WINDOW));
    currentCount.set(count);
  }

  long currentValue() {
    return currentCount.get();
  }
}
