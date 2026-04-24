package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Temporary {@link NotificationSender} for non-prod environments where the real Resilience4j +
 * DLT client (group 7) isn't wired yet. Logs at WARN so its presence is visible; never silently
 * absorbs notifications.
 *
 * <p>Guarded by {@code @Profile("!prod")} so it cannot activate in production by accident — if a
 * prod deploy happens before group 7, the missing {@link NotificationSender} bean will fail
 * context load and crash the pod, which is the right failure mode.
 *
 * <p>Replaced automatically by any registered {@link NotificationSender} bean (e.g., group 7's
 * {@code NotificationClient}).
 */
@Component
@Profile("!prod")
@ConditionalOnMissingBean(NotificationSender.class)
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(NotificationEvent event) {
    log.warn(
        "[STUB SENDER] notification dropped — plan={} {} -> {} v{} (real sender not yet wired; group 7)",
        event.planId(),
        event.from(),
        event.to(),
        event.planVersion());
  }
}
