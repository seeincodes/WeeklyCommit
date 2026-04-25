package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.PlanState;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manager team rollup view (per USER_FLOW.md). Aggregates a week's plans for a manager into the
 * dashboard payload.
 *
 * <p>{@code byOutcome} is intentionally absent in v1 — it requires RCDO outcome metadata that the
 * backend doesn't have until group 7's RCDO client lands. Re-introduce as a follow-up; the frontend
 * can call {@code GET /rcdo/supporting-outcomes} directly to enrich its own view in the meantime.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RollupResponse(
    BigDecimal alignmentPct,
    BigDecimal completionPct,
    Map<String, Integer> tierDistribution,
    int unreviewedCount,
    int stuckCommitCount,
    List<MemberCard> members) {

  /** Per-employee summary surfaced in the manager rollup. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MemberCard(
      UUID employeeId,
      String name,
      PlanState planState,
      TopRockSummary topRock,
      Map<String, Integer> tierCounts,
      String reflectionPreview,
      List<String> flags) {}

  /** Lightweight top-rock representation embedded in member cards. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record TopRockSummary(UUID commitId, String title) {}
}
