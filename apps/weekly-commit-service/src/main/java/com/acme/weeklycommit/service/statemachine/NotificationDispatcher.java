package com.acme.weeklycommit.service.statemachine;

/**
 * Contract the state machine uses to signal "a transition committed; fire a notification later."
 *
 * <p>Production implementation registers a {@code TransactionSynchronization} that runs after
 * commit. Unit tests mock this interface directly. See {@code
 * integration.notification.TransactionAwareNotificationDispatcher} (group 5 cycle 10 / group 7).
 */
public interface NotificationDispatcher {

  /**
   * Arrange for the given event to be delivered to downstream notification-svc after the current
   * transaction commits. Never fires if the transaction rolls back.
   */
  void dispatchAfterCommit(NotificationEvent event);
}
