package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.PlanState;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /plans/{id}/transitions}.
 *
 * <p>{@code to} is the target state. Validation accepts any {@link PlanState} here; the state
 * machine's transition table is the source of truth for which targets are legal from a given
 * current state, and produces a clean {@code 422 INVALID_STATE_TRANSITION} envelope if an IC
 * passes e.g. {@code DRAFT} or {@code ARCHIVED}.
 */
public record TransitionRequest(@NotNull PlanState to) {}
