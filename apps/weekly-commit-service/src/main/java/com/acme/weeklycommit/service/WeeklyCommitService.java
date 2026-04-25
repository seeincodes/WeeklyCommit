package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.dto.CreateCommitRequest;
import com.acme.weeklycommit.api.dto.UpdateCommitRequest;
import com.acme.weeklycommit.api.exception.InvalidStateTransitionException;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write operations against {@link WeeklyCommit}. Authz decided here (service boundary) per the
 * project memory. Derived-field enrichment is a controller concern — this service returns raw
 * entities.
 *
 * <p>Every method that takes a {@code planId} first loads the plan for authz, then reads/writes the
 * commit. The plan load is unavoidable: commits carry {@code planId} but not {@code employeeId},
 * and authz is scoped to "caller owns the plan" (or MANAGER role).
 */
@Service
public class WeeklyCommitService {

  private final WeeklyPlanRepository plans;
  private final WeeklyCommitRepository commits;
  private final Clock clock;

  public WeeklyCommitService(
      WeeklyPlanRepository plans, WeeklyCommitRepository commits, Clock clock) {
    this.plans = plans;
    this.commits = commits;
    this.clock = clock;
  }

  /**
   * List commits for a plan, ordered by {@code displayOrder}. Self-or-MANAGER: caller must own the
   * plan or hold the MANAGER role. 404 if the plan is missing (raised before the commits query so a
   * peer's plan presence can't be probed via timing).
   */
  @Transactional(readOnly = true)
  public List<WeeklyCommit> findCommitsForPlan(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan = requireReadableByCaller(planId, caller);
    return commits.findByPlanIdOrderByDisplayOrderAsc(plan.getId());
  }

  /**
   * Create a new commit on a DRAFT plan. Owner-only — even a MANAGER cannot create commits on
   * another IC's plan. Only valid in DRAFT state; any other state throws {@link
   * InvalidStateTransitionException}.
   *
   * <p>{@code displayOrder} is auto-assigned to {@code max(existing)+1} if the request omits it;
   * otherwise honored as given.
   */
  @Transactional
  public WeeklyCommit createCommit(
      UUID planId, CreateCommitRequest request, AuthenticatedPrincipal caller) {
    WeeklyPlan plan = requireOwnedByCaller(planId, caller);
    requireDraftForMutation(plan);

    int displayOrder =
        request.displayOrder() != null ? request.displayOrder() : nextDisplayOrder(planId);

    WeeklyCommit commit =
        new WeeklyCommit(
            UUID.randomUUID(),
            planId,
            request.title(),
            request.supportingOutcomeId(),
            request.chessTier(),
            displayOrder);
    commit.setDescription(request.description());
    commit.setEstimatedHours(request.estimatedHours());
    commit.setRelatedMeeting(request.relatedMeeting());
    commit.setCategoryTags(
        request.categoryTags() == null
            ? new String[0]
            : request.categoryTags().toArray(String[]::new));
    return commits.save(commit);
  }

  private int nextDisplayOrder(UUID planId) {
    return commits.findByPlanIdOrderByDisplayOrderAsc(planId).stream()
            .mapToInt(WeeklyCommit::getDisplayOrder)
            .max()
            .orElse(-1)
        + 1;
  }

  /**
   * Load the plan and enforce self-or-MANAGER authz. Throws {@link ResourceNotFoundException} if
   * missing (so peer existence can't be probed) and {@link AccessDeniedException} if the caller is
   * unrelated.
   */
  private WeeklyPlan requireReadableByCaller(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    if (!plan.getEmployeeId().equals(caller.employeeId()) && !caller.isManager()) {
      throw new AccessDeniedException(
          "caller " + caller.employeeId() + " cannot access commits on plan " + planId);
    }
    return plan;
  }

  /**
   * Load the plan and enforce owner-only authz. Stricter than {@link #requireReadableByCaller} —
   * MANAGER role does NOT grant write access on someone else's plan.
   */
  private WeeklyPlan requireOwnedByCaller(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    if (!plan.getEmployeeId().equals(caller.employeeId())) {
      throw new AccessDeniedException(
          "caller "
              + caller.employeeId()
              + " cannot mutate commits on plan "
              + planId
              + " (owned by "
              + plan.getEmployeeId()
              + ")");
    }
    return plan;
  }

  /** Commit CRUD is DRAFT-only. Anything else rejects with the standard 422 envelope. */
  private static void requireDraftForMutation(WeeklyPlan plan) {
    if (plan.getState() != PlanState.DRAFT) {
      throw new InvalidStateTransitionException(
          plan.getState().name(), "DRAFT", "commit mutation only allowed in DRAFT state");
    }
  }

  /**
   * State-aware update. DRAFT accepts definition fields; LOCKED past day-4 accepts only actual*
   * fields; other states / windows reject outright. Owner-only.
   */
  @Transactional
  public WeeklyCommit updateCommit(
      UUID commitId, UpdateCommitRequest request, AuthenticatedPrincipal caller) {
    WeeklyCommit commit =
        commits
            .findById(commitId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyCommit", commitId));
    WeeklyPlan plan = requireOwnedByCaller(commit.getPlanId(), caller);

    PlanState state = plan.getState();
    if (state == PlanState.DRAFT) {
      if (request.touchesActualFields()) {
        throw new InvalidStateTransitionException(
            state.name(),
            "DRAFT",
            "actualStatus / actualNote not mutable in DRAFT — transition to LOCKED first");
      }
      applyDefinitionFields(commit, request);
    } else if (state == PlanState.LOCKED) {
      // Reconciliation window: now >= weekStart + 4 days (UTC start-of-day).
      Instant opensAt = plan.getWeekStart().plusDays(4).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (Instant.now(clock).isBefore(opensAt)) {
        throw new InvalidStateTransitionException(
            state.name(), "RECONCILIATION_MODE", "reconciliation window opens at " + opensAt);
      }
      if (request.touchesDefinitionFields()) {
        throw new InvalidStateTransitionException(
            state.name(),
            "RECONCILIATION_MODE",
            "definition fields are immutable once the week is locked");
      }
      applyActualFields(commit, request);
    } else {
      // RECONCILED or ARCHIVED — no mutations at all.
      throw new InvalidStateTransitionException(
          state.name(), "DRAFT_OR_RECONCILIATION_MODE", "commit is immutable in state " + state);
    }

    return commits.save(commit);
  }

  private static void applyDefinitionFields(WeeklyCommit commit, UpdateCommitRequest r) {
    if (r.title() != null) {
      commit.setTitle(r.title());
    }
    if (r.description() != null) {
      commit.setDescription(r.description());
    }
    if (r.supportingOutcomeId() != null) {
      commit.setSupportingOutcomeId(r.supportingOutcomeId());
    }
    if (r.chessTier() != null) {
      commit.setChessTier(r.chessTier());
    }
    if (r.categoryTags() != null) {
      commit.setCategoryTags(r.categoryTags().toArray(String[]::new));
    }
    if (r.estimatedHours() != null) {
      commit.setEstimatedHours(r.estimatedHours());
    }
    if (r.displayOrder() != null) {
      commit.setDisplayOrder(r.displayOrder());
    }
    if (r.relatedMeeting() != null) {
      commit.setRelatedMeeting(r.relatedMeeting());
    }
  }

  private static void applyActualFields(WeeklyCommit commit, UpdateCommitRequest r) {
    if (r.actualStatus() != null) {
      commit.setActualStatus(r.actualStatus());
    }
    if (r.actualNote() != null) {
      commit.setActualNote(r.actualNote());
    }
  }

  /**
   * Carry a commit forward into next week's DRAFT plan. Source plan must be in a post-reconcile
   * state: either RECONCILED, or LOCKED with the reconciliation window open (now ≥ weekStart + 4
   * days). Owner-only.
   *
   * <p>If next week's plan doesn't exist, one is auto-created (DRAFT, same employee). If the source
   * has already been carried forward (idempotent guard via {@code carriedForwardToId}), the
   * existing twin is returned without creating a duplicate.
   */
  @Transactional
  public WeeklyCommit carryForwardCommit(UUID sourceCommitId, AuthenticatedPrincipal caller) {
    WeeklyCommit source =
        commits
            .findById(sourceCommitId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyCommit", sourceCommitId));
    WeeklyPlan sourcePlan = requireOwnedByCaller(source.getPlanId(), caller);
    requireCarryForwardable(sourcePlan);

    // Idempotent guard.
    if (source.getCarriedForwardToId() != null) {
      return commits
          .findById(source.getCarriedForwardToId())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "invariant: commit "
                          + sourceCommitId
                          + " has carriedForwardToId "
                          + source.getCarriedForwardToId()
                          + " but that target does not exist"));
    }

    LocalDate nextWeek = sourcePlan.getWeekStart().plusDays(7);
    WeeklyPlan targetPlan =
        plans
            .findByEmployeeIdAndWeekStart(sourcePlan.getEmployeeId(), nextWeek)
            .orElseGet(
                () ->
                    plans.save(
                        new WeeklyPlan(UUID.randomUUID(), sourcePlan.getEmployeeId(), nextWeek)));

    WeeklyCommit twin =
        new WeeklyCommit(
            UUID.randomUUID(),
            targetPlan.getId(),
            source.getTitle(),
            source.getSupportingOutcomeId(),
            source.getChessTier(),
            nextDisplayOrder(targetPlan.getId()));
    twin.setDescription(source.getDescription());
    twin.setEstimatedHours(source.getEstimatedHours());
    twin.setRelatedMeeting(source.getRelatedMeeting());
    twin.setCategoryTags(source.getCategoryTags());
    twin.setCarriedForwardFromId(source.getId());
    WeeklyCommit savedTwin = commits.save(twin);

    // Back-reference so DerivedFieldService's streak walk can link both directions.
    source.setCarriedForwardToId(savedTwin.getId());
    commits.save(source);

    return savedTwin;
  }

  /**
   * Carry-forward is only valid from RECONCILED plans or LOCKED plans whose reconciliation window
   * is already open. Anything else rejects with the standard 422 envelope.
   */
  private void requireCarryForwardable(WeeklyPlan plan) {
    PlanState state = plan.getState();
    if (state == PlanState.RECONCILED) {
      return;
    }
    if (state == PlanState.LOCKED) {
      Instant opensAt = plan.getWeekStart().plusDays(4).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (!Instant.now(clock).isBefore(opensAt)) {
        return;
      }
    }
    throw new InvalidStateTransitionException(
        state.name(),
        "CARRIED_FORWARD",
        "carry-forward requires RECONCILED or LOCKED-in-reconciliation-mode state");
  }

  /**
   * Delete a commit. Owner-only, DRAFT-only. The {@code ON DELETE SET NULL} FK constraints on
   * {@code carried_forward_from_id} / {@code carried_forward_to_id} mean neighbouring commits
   * automatically have their back-references nulled — no manual cleanup here.
   */
  @Transactional
  public void deleteCommit(UUID commitId, AuthenticatedPrincipal caller) {
    WeeklyCommit commit =
        commits
            .findById(commitId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyCommit", commitId));
    WeeklyPlan plan = requireOwnedByCaller(commit.getPlanId(), caller);
    requireDraftForMutation(plan);
    commits.delete(commit);
  }
}
