package com.acme.weeklycommit.domain.enums;

/**
 * Outcome set by the IC during reconciliation. {@link #PENDING} is the default until the IC
 * evaluates the commit on day-4+.
 */
public enum ActualStatus {
  PENDING,
  DONE,
  PARTIAL,
  MISSED
}
