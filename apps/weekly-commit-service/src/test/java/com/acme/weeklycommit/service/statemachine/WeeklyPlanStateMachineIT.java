package com.acme.weeklycommit.service.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full-context integration test for {@link WeeklyPlanStateMachine}. Proves that the
 * {@code @Transactional} proxy actually commits the plan mutation and audit row together, and that
 * {@code @Version} optimistic locking fires on concurrent updates — behaviors that unit tests
 * with mocked repositories cannot verify.
 *
 * <p>Not transactional at the test level (no {@code @Transactional} here): the point is to
 * observe committed state, not a rolled-back test transaction. Cleanup happens in
 * {@link #truncate()}.
 *
 * <p>{@link NotificationDispatcher} is mocked to avoid standing up the real
 * {@code TransactionAwareNotificationDispatcher} — its commit-timing contract is already covered
 * by unit tests in {@code TransactionAwareNotificationDispatcherTest}.
 */
@SpringBootTest
class WeeklyPlanStateMachineIT {

  @Autowired private WeeklyPlanStateMachine stateMachine;
  @Autowired private WeeklyPlanRepository plans;
  @Autowired private AuditLogRepository audits;

  @MockBean private NotificationDispatcher dispatcher;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresTestContainer.register(registry);
    // Stub Auth0 values so SecurityConfig wires without reaching out for JWKs.
    registry.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    registry.add("AUTH0_AUDIENCE", () -> "test-audience");
  }

  @BeforeEach
  void truncate() {
    audits.deleteAll();
    plans.deleteAll();
  }

  @Test
  void transition_commitsPlanAndAuditAtomically() {
    UUID planId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    plans.saveAndFlush(new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27")));

    stateMachine.transition(planId, PlanState.LOCKED, actorId);

    WeeklyPlan reloaded = plans.findById(planId).orElseThrow();
    assertThat(reloaded.getState()).isEqualTo(PlanState.LOCKED);
    assertThat(reloaded.getLockedAt()).isNotNull();

    List<AuditLog> auditRows =
        audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            AuditEntityType.WEEKLY_PLAN, planId);
    assertThat(auditRows).hasSize(1);
    assertThat(auditRows.get(0))
        .satisfies(
            row -> {
              assertThat(row.getFromState()).isEqualTo("DRAFT");
              assertThat(row.getToState()).isEqualTo("LOCKED");
              assertThat(row.getActorId()).isEqualTo(actorId);
            });

    verify(dispatcher).dispatchAfterCommit(any(NotificationEvent.class));
  }

  @Test
  void transition_systemActor_writesNullActorId() {
    UUID planId = UUID.randomUUID();
    plans.saveAndFlush(new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27")));

    stateMachine.transition(planId, PlanState.LOCKED, null);

    List<AuditLog> rows =
        audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            AuditEntityType.WEEKLY_PLAN, planId);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getActorId()).isNull();
  }

  @Test
  void optimisticLock_concurrentUpdate_throws() {
    // Two "tabs" load the same plan; first saves, second tries to save a stale version.
    UUID planId = UUID.randomUUID();
    plans.saveAndFlush(new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27")));

    WeeklyPlan tabOne = plans.findById(planId).orElseThrow();
    WeeklyPlan tabTwo = plans.findById(planId).orElseThrow();

    tabOne.setReflectionNote("first save");
    plans.saveAndFlush(tabOne);

    tabTwo.setReflectionNote("stale save");
    assertThatThrownBy(() -> plans.saveAndFlush(tabTwo))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  void idempotentNoop_doesNotWriteAudit() {
    // Pre-populate a plan in LOCKED state directly, then call transition(LOCKED). The state
    // machine must treat this as a no-op: no audit row appended, no dispatcher invocation.
    UUID planId = UUID.randomUUID();
    WeeklyPlan seed = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    seed.setState(PlanState.LOCKED);
    seed.setLockedAt(java.time.Instant.parse("2026-04-27T17:00:00Z"));
    plans.saveAndFlush(seed);

    stateMachine.transition(planId, PlanState.LOCKED, UUID.randomUUID());

    assertThat(
            audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                AuditEntityType.WEEKLY_PLAN, planId))
        .isEmpty();
    verify(dispatcher, org.mockito.Mockito.never()).dispatchAfterCommit(any());
  }
}
