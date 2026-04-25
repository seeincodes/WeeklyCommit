package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.WeeklyCommitMapper;
import com.acme.weeklycommit.api.dto.WeeklyCommitResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.service.DerivedFieldService;
import com.acme.weeklycommit.service.WeeklyCommitService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for {@link WeeklyCommit}. Thin by design — composes {@link WeeklyCommitService}
 * (authz + persistence) with {@link DerivedFieldService} (computed fields) to produce the wire
 * DTO.
 *
 * <p>All endpoints emit the standard {@link ApiEnvelope}. Authz is enforced at the service
 * boundary (memory §security); the controller only validates request shape.
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
   * List commits for a plan, ordered by {@code displayOrder}. Self-or-MANAGER authz enforced in
   * the service. Each response item includes the computed {@code derived} object (carryStreak +
   * stuckFlag) so the UI can render badges without a second round-trip.
   *
   * <p>Performance: one {@code DerivedFieldService.deriveFor} call per commit; each walks up to
   * 52 repo rows. Acceptable at v1 volume (≤ ~10 commits/plan × 52 hops = ~520 queries worst
   * case). The recursive CTE rewrite is documented on {@code DerivedFieldService.carryStreak}.
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
}
