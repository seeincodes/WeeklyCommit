package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.service.statemachine.NotificationDispatcher;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Production {@link NotificationDispatcher} that defers delivery until the enclosing transaction
 * commits — see MEMO decision #2 (synchronous notification after commit) and docs/ERROR_FIX_LOG.md
 * note on why the notification call lives outside the transaction.
 *
 * <p>If called without an active transaction (e.g. from an ad-hoc scheduled job), falls back to
 * immediate send. This is deliberate — no silent drop of notifications when the caller happens to
 * be non-transactional.
 *
 * <p>Delivery is delegated to {@link NotificationSender}. This class owns the <i>when</i>; the
 * sender owns the <i>how</i>.
 */
@Component
public class TransactionAwareNotificationDispatcher implements NotificationDispatcher {

  private static final Logger log =
      LoggerFactory.getLogger(TransactionAwareNotificationDispatcher.class);

  private final NotificationSender sender;

  public TransactionAwareNotificationDispatcher(NotificationSender sender) {
    this.sender = sender;
  }

  @Override
  public void dispatchAfterCommit(NotificationEvent event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              sender.send(event);
            }
          });
      return;
    }

    // No active transaction: legitimate for one-off scheduled-job paths, but unexpected for
    // controllers. Log at WARN so a misconfigured controller-path caller surfaces in logs
    // rather than shipping a notification inside a "transaction" that never existed.
    log.warn(
        "dispatchAfterCommit called without an active transaction — sending eagerly "
            + "(plan={} {} -> {} v{}). If this is a controller path, callers should be @Transactional.",
        event.planId(),
        event.from(),
        event.to(),
        event.planVersion());
    sender.send(event);
  }
}
