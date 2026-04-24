package com.acme.weeklycommit.integration.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TransactionAwareNotificationDispatcherTest {

  @Mock private NotificationSender sender;

  @AfterEach
  void tearDownSync() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clear();
    }
  }

  private static NotificationEvent event() {
    return new NotificationEvent(UUID.randomUUID(), PlanState.DRAFT, PlanState.LOCKED, 3L);
  }

  @Test
  void dispatch_inActiveTransaction_defersUntilAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionAwareNotificationDispatcher d = new TransactionAwareNotificationDispatcher(sender);
    NotificationEvent e = event();

    d.dispatchAfterCommit(e);

    // Not sent yet — we're still "mid-transaction"
    verify(sender, never()).send(any());
    assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

    // Simulate commit by firing the registered callback ourselves.
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }

    verify(sender).send(e);
  }

  @Test
  void dispatch_noActiveTransaction_sendsImmediately() {
    // No initSynchronization() — simulates a non-transactional caller (e.g. ad-hoc scheduled job).
    TransactionAwareNotificationDispatcher d = new TransactionAwareNotificationDispatcher(sender);
    NotificationEvent e = event();

    d.dispatchAfterCommit(e);

    verify(sender).send(e);
  }

  @Test
  void dispatch_inActiveTransaction_rolledBack_neverSends() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionAwareNotificationDispatcher d = new TransactionAwareNotificationDispatcher(sender);

    d.dispatchAfterCommit(event());

    // Rollback is modelled by NEVER invoking afterCommit on the registered syncs.
    TransactionSynchronizationManager.clear();

    verify(sender, never()).send(any());
  }
}
