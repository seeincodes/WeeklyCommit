package com.acme.weeklycommit.integration.notification;

/**
 * Thrown by {@link NotificationClient} when notification-svc returns 400. Per ADR-0002 a 400 is
 * non-recoverable: a retry won't fix bad request data, and writing a DLT row for it would just
 * accumulate noise. The wrapping {@link ResilientNotificationSender} catches this distinctly from
 * {@link org.springframework.web.reactive.function.client.WebClientResponseException} so it can
 * skip both retry and DLT.
 */
public class NotificationValidationException extends RuntimeException {

  public NotificationValidationException(String message) {
    super(message);
  }
}
