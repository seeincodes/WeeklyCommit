package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.service.statemachine.NotificationEvent;

/**
 * Final target for a notification event — typically the HTTP client that talks to notification-svc
 * (ADR-0002). Separated from {@link
 * com.acme.weeklycommit.service.statemachine.NotificationDispatcher} so the "defer until commit"
 * concern and the "actually send it" concern can evolve independently.
 *
 * <p>The concrete implementation ({@code NotificationClient} with Resilience4j + DLT) ships in
 * group 7. This contract lets cycle 10 stand up the dispatcher without waiting on it.
 */
public interface NotificationSender {
  void send(NotificationEvent event);
}
