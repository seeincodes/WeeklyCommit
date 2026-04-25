package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.PlanState;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API-layer view of a {@code WeeklyPlan}. Separate from the JPA entity so column renames on the DB
 * side don't leak into the wire format (maintainability per memory).
 *
 * <p>Wire shape is stable across the {@code /plans/*} endpoints; see docs/USER_FLOW.md.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WeeklyPlanResponse(
    UUID id,
    UUID employeeId,
    LocalDate weekStart,
    PlanState state,
    Instant lockedAt,
    Instant reconciledAt,
    Instant managerReviewedAt,
    String reflectionNote,
    long version) {}
