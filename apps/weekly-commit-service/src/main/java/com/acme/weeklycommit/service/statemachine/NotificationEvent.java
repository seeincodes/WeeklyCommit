package com.acme.weeklycommit.service.statemachine;

import com.acme.weeklycommit.domain.enums.PlanState;
import java.util.UUID;

/**
 * Immutable description of a successful state transition, handed off to the notification path for
 * post-commit dispatch. The {@code planVersion} is the post-save version, useful as part of the
 * idempotency key sent to notification-svc (ADR-0002: {@code X-Idempotency-Key = wc-plan-{planId}
 * -{to}-v{planVersion}}).
 */
public record NotificationEvent(UUID planId, PlanState from, PlanState to, long planVersion) {}
