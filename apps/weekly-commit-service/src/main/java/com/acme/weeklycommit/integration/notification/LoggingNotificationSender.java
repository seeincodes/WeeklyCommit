package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@link NotificationSender} for the pre-group-7 window. Logs and returns. Replaced by
 * the real {@code NotificationClient} (Resilience4j + DLT) once ADR-0002 + group 7 land.
 *
 * <p>{@code @ConditionalOnMissingBean} so it steps aside the moment a real sender bean exists.
 */
@Component
@ConditionalOnMissingBean(
    value = NotificationSender.class,
    ignored = LoggingNotificationSender.class)
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(NotificationEvent event) {
    log.info(
        "[STUB] notification suppressed — plan={} {} -> {} v{} (real sender ships in group 7)",
        event.planId(),
        event.from(),
        event.to(),
        event.planVersion());
  }
}
