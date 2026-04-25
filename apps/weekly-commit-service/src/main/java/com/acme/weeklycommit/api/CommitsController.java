package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.CreateCommitRequest;
import com.acme.weeklycommit.api.dto.UpdateCommitRequest;
import com.acme.weeklycommit.api.dto.WeeklyCommitMapper;
import com.acme.weeklycommit.api.dto.WeeklyCommitResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.service.DerivedFieldService;
import com.acme.weeklycommit.service.WeeklyCommitService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for {@link WeeklyCommit}. Thin by design — composes {@link WeeklyCommitService}
 * (authz + persistence) with {@link DerivedFieldService} (computed fields) to produce the wire DTO.
 *
 * <p>All endpoints emit the standard {@link ApiEnvelope}. Authz is enforced at the service boundary
 * (memory §security); the controller only validates request shape.
 */
@RestController
@RequestMapping("/api/v1")
public class CommitsController {

  private final WeeklyCommitService commitService;
  private final DerivedFieldService derivedFieldService;
  private final WeeklyCommitMapper mapper;

  public CommitsController(
      WeeklyCommitService commitService,
      DerivedFieldService derivedFieldService,
      WeeklyCommitMapper mapper) {
    this.commitService = commitService;
    this.derivedFieldService = derivedFieldService;
    this.mapper = mapper;
  }

  /**
   * List commits for a plan, ordered by {@code displayOrder}. Self-or-MANAGER authz enforced in the
   * service. Each response item includes the computed {@code derived} object (carryStreak +
   * stuckFlag) so the UI can render badges without a second round-trip.
   *
   * <p>Performance: one {@code DerivedFieldService.deriveFor} call per commit; each walks up to 52
   * repo rows. Acceptable at v1 volume (≤ ~10 commits/plan × 52 hops = ~520 queries worst case).
   * The recursive CTE rewrite is documented on {@code DerivedFieldService.carryStreak}.
   */
  @GetMapping("/plans/{planId}/commits")
  public ResponseEntity<ApiEnvelope<List<WeeklyCommitResponse>>> listCommits(
      @PathVariable UUID planId, AuthenticatedPrincipal caller) {
    List<WeeklyCommit> commits = commitService.findCommitsForPlan(planId, caller);
    List<WeeklyCommitResponse> body =
        commits.stream()
            .map(
                c -> {
                  DerivedFieldService.Derived d = derivedFieldService.deriveFor(c.getId());
                  return mapper.toResponse(c, d.carryStreak(), d.stuckFlag());
                })
            .toList();
    return ResponseEntity.ok(ApiEnvelope.of(body));
  }

  /**
   * Create a new commit on the given DRAFT plan. Owner-only authz (service enforces). Returns
   * 201 with the created commit in the envelope, carryStreak=1 by construction (new commits are
   * never carried from anywhere at creation time — derived computed uniformly for consistency).
   */
  @PostMapping("/plans/{planId}/commits")
  public ResponseEntity<ApiEnvelope<WeeklyCommitResponse>> createCommit(
      @PathVariable UUID planId,
      @Valid @RequestBody CreateCommitRequest request,
      AuthenticatedPrincipal caller) {
    WeeklyCommit saved = commitService.createCommit(planId, request, caller);
    DerivedFieldService.Derived d = derivedFieldService.deriveFor(saved.getId());
    WeeklyCommitResponse body = mapper.toResponse(saved, d.carryStreak(), d.stuckFlag());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(body));
  }

  /**
   * State-aware partial update. In DRAFT the definition fields (title, description, chessTier,
   * ...) are mutable; in LOCKED past day-4 only actual* fields are mutable. Owner-only authz
   * and state/window enforcement live in the service.
   */
  @PatchMapping("/commits/{commitId}")
  public ResponseEntity<ApiEnvelope<WeeklyCommitResponse>> updateCommit(
      @PathVariable UUID commitId,
      @Valid @RequestBody UpdateCommitRequest request,
      AuthenticatedPrincipal caller) {
    WeeklyCommit saved = commitService.updateCommit(commitId, request, caller);
    DerivedFieldService.Derived d = derivedFieldService.deriveFor(saved.getId());
    WeeklyCommitResponse body = mapper.toResponse(saved, d.carryStreak(), d.stuckFlag());
    return ResponseEntity.ok(ApiEnvelope.of(body));
  }

  /**
   * Delete a commit. Owner-only, DRAFT-only (service enforces). Returns 204 with no body.
   * Carry-forward back-references on neighbouring commits are nulled by the FK {@code ON DELETE
   * SET NULL} constraint.
   */
  @DeleteMapping("/commits/{commitId}")
  public ResponseEntity<Void> deleteCommit(
      @PathVariable UUID commitId, AuthenticatedPrincipal caller) {
    commitService.deleteCommit(commitId, caller);
    return ResponseEntity.noContent().build();
  }
}
