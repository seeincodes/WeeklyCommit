package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub {@link NotificationSender} for non-prod environments where the real notification-svc is not
 * reachable. Logs at WARN so its presence is visible; never silently absorbs notifications.
 *
 * <p>Profile-gated to {@code !prod}, mutually exclusive with the {@code @Profile("prod")} {@link
 * ResilientNotificationSender}. The earlier {@code @ConditionalOnMissingBean} guard was removed
 * because it was self-conflicting (the bean implements {@link NotificationSender} itself, and
 * Spring's missing-bean check treated that as "already present" and skipped registration — dropping
 * any {@code @SpringBootTest} that depends on a NotificationSender into a context-load failure).
 */
@Component
@Profile("!prod")
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
