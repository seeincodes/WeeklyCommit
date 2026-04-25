package com.acme.weeklycommit.api.exception;

/**
 * Thrown by {@code WeeklyPlanStateMachine} when a guard rejects a requested transition (e.g.
 * DRAFT→RECONCILED, or LOCKED→RECONCILED before day-4).
 *
 * <p>Maps to HTTP 422 in {@link GlobalExceptionHandler}; the handler surfaces {@link
 * #getFromState()} and {@link #getToState()} in the response {@code meta}.
 *
 * <p><b>Followup (v1.1):</b> the reason string today embeds machine-readable Instants (e.g.
 * "reconciliation window opens at 2026-05-02T00:00:00Z") which is fine for logs but opaque for end
 * users. A senior polish pass should split the reason into a user-facing message and a set of typed
 * fields (e.g. {@code windowOpensAt}) that the exception handler places in {@code meta} — matching
 * the {@code fromState}/{@code toState} pattern already there.
 */
public class InvalidStateTransitionException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final String fromState;
  private final String toState;

  public InvalidStateTransitionException(String fromState, String toState, String reason) {
    super("Invalid transition %s -> %s: %s".formatted(fromState, toState, reason));
    this.fromState = fromState;
    this.toState = toState;
  }

  public String getFromState() {
    return fromState;
  }

  public String getToState() {
    return toState;
  }
}
