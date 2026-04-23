package com.acme.weeklycommit.api.exception;

/**
 * Thrown by {@code WeeklyPlanStateMachine} when a guard rejects a requested transition (e.g.
 * DRAFT→RECONCILED, or LOCKED→RECONCILED before day-4).
 *
 * <p>Maps to HTTP 422 in {@link GlobalExceptionHandler}.
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
