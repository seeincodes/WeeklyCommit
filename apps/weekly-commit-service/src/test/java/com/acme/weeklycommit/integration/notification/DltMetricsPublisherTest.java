package com.acme.weeklycommit.integration.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.repo.NotificationDltRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DltMetricsPublisherTest {

  @Mock private NotificationDltRepository dltRepo;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-04-25T10:00:00Z"), ZoneId.of("UTC"));

  @Test
  void register_publishesGaugeFromCurrentCount() {
    when(dltRepo.countCreatedSince(any()))
        .thenReturn(0L, 3L); // first call (register), then refresh
    DltMetricsPublisher publisher = new DltMetricsPublisher(dltRepo, meterRegistry, fixedClock);

    publisher.register();
    assertThat(meterRegistry.get("weekly_commit.notification.dlt.recent_count").gauge().value())
        .isEqualTo(0d);

    publisher.refresh();
    assertThat(meterRegistry.get("weekly_commit.notification.dlt.recent_count").gauge().value())
        .isEqualTo(3d);
  }

  @Test
  void refresh_queriesAtNowMinusOneHour() {
    when(dltRepo.countCreatedSince(any())).thenReturn(0L, 7L);
    DltMetricsPublisher publisher = new DltMetricsPublisher(dltRepo, meterRegistry, fixedClock);
    publisher.register();

    publisher.refresh();

    org.mockito.ArgumentCaptor<Instant> sinceCaptor =
        org.mockito.ArgumentCaptor.forClass(Instant.class);
    org.mockito.Mockito.verify(dltRepo, org.mockito.Mockito.atLeast(2))
        .countCreatedSince(sinceCaptor.capture());
    // last refresh window: now - 1h = 2026-04-25T09:00:00Z
    assertThat(sinceCaptor.getValue()).isEqualTo(Instant.parse("2026-04-25T09:00:00Z"));
    assertThat(publisher.currentValue()).isEqualTo(7L);
  }
}
