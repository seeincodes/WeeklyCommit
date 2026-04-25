package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * API-layer view of a {@code WeeklyCommit}, including presearch-§3 derived fields surfaced as a
 * nested {@code derived} object. Keeping derived separate from raw commit data makes the wire
 * contract honest about what the backend computed vs. what it persisted.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WeeklyCommitResponse(
    UUID id,
    UUID planId,
    String title,
    String description,
    UUID supportingOutcomeId,
    ChessTier chessTier,
    List<String> categoryTags,
    BigDecimal estimatedHours,
    int displayOrder,
    String relatedMeeting,
    UUID carriedForwardFromId,
    UUID carriedForwardToId,
    ActualStatus actualStatus,
    String actualNote,
    Derived derived) {

  /**
   * Derived, non-stored fields computed by {@code DerivedFieldService}:
   *
   * <ul>
   *   <li>{@code carryStreak} — chain length via {@code carriedForwardFromId} walk (capped at 52)
   *   <li>{@code stuckFlag} — {@code carryStreak >= 3}
   * </ul>
   */
  public record Derived(int carryStreak, boolean stuckFlag) {}
}
