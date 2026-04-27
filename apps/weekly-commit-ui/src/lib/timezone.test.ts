import { describe, it, expect } from 'vitest';
import {
  currentWeekStart,
  isReconcileEligible,
  formatInstant,
  getEmployeeTimezone,
  RECONCILE_OFFSET_DAYS,
} from './timezone';

describe('timezone helpers', () => {
  describe('isReconcileEligible', () => {
    it('returns false before weekStart + 4 days in the given tz', () => {
      // weekStart Mon 2026-04-27 in America/New_York (UTC-04:00 in late April).
      // Threshold = Fri 2026-05-01 00:00 local = 2026-05-01T04:00:00Z.
      const beforeThreshold = new Date('2026-05-01T03:59:59Z');
      expect(isReconcileEligible('2026-04-27', beforeThreshold, 'America/New_York')).toBe(false);
    });

    it('returns true at weekStart + 4 days exactly in the given tz', () => {
      // 2026-05-01T04:00:00Z is 2026-05-01 00:00 EDT.
      const atThreshold = new Date('2026-05-01T04:00:00Z');
      expect(isReconcileEligible('2026-04-27', atThreshold, 'America/New_York')).toBe(true);
    });

    it('returns true after weekStart + 4 days in the given tz', () => {
      const afterThreshold = new Date('2026-05-01T15:00:00Z'); // Fri 11am EDT
      expect(isReconcileEligible('2026-04-27', afterThreshold, 'America/New_York')).toBe(true);
    });

    it('handles UTC tz consistently with the inline UTC helper that came before', () => {
      // weekStart Mon 2026-04-27 UTC. Threshold = Fri 2026-05-01 00:00 UTC.
      const before = new Date('2026-05-01T00:00:00Z');
      const atOrAfter = new Date('2026-05-01T00:00:01Z');
      expect(isReconcileEligible('2026-04-27', before, 'UTC')).toBe(true);
      expect(isReconcileEligible('2026-04-27', atOrAfter, 'UTC')).toBe(true);

      const wayBefore = new Date('2026-04-30T23:59:59Z');
      expect(isReconcileEligible('2026-04-27', wayBefore, 'UTC')).toBe(false);
    });

    it('handles a DST spring-forward boundary correctly (US: Mar 8 2026, 02:00->03:00)', () => {
      // weekStart Mon 2026-03-02 in America/New_York. Threshold = Fri 2026-03-06 00:00 EST,
      // which is 2026-03-06T05:00:00Z (still standard time -- DST flips Mar 8).
      const justBeforeThreshold = new Date('2026-03-06T04:59:59Z');
      const justAfterThreshold = new Date('2026-03-06T05:00:00Z');
      expect(isReconcileEligible('2026-03-02', justBeforeThreshold, 'America/New_York')).toBe(
        false,
      );
      expect(isReconcileEligible('2026-03-02', justAfterThreshold, 'America/New_York')).toBe(true);
    });

    it('handles a DST fall-back week (Nov 1 2026)', () => {
      // weekStart Mon 2026-10-26 in America/New_York. Threshold Fri 2026-10-30 00:00 EDT
      // (still daylight time on Oct 30 -- fall-back Nov 1) = 2026-10-30T04:00:00Z.
      const justBeforeThreshold = new Date('2026-10-30T03:59:59Z');
      const justAfterThreshold = new Date('2026-10-30T04:00:00Z');
      expect(isReconcileEligible('2026-10-26', justBeforeThreshold, 'America/New_York')).toBe(
        false,
      );
      expect(isReconcileEligible('2026-10-26', justAfterThreshold, 'America/New_York')).toBe(true);
    });

    it('exposes RECONCILE_OFFSET_DAYS = 4 for downstream callers', () => {
      expect(RECONCILE_OFFSET_DAYS).toBe(4);
    });
  });

  describe('formatInstant', () => {
    it('formats an Instant string into the given tz with the given options', () => {
      const out = formatInstant(
        '2026-05-01T15:00:00Z',
        { dateStyle: 'short', timeStyle: 'short' },
        'America/New_York',
      );
      // 11:00 EDT on May 1, 2026. Locale-independent assertions are tricky;
      // assert the time-portion contains "11" and the date contains "5/1".
      expect(out).toMatch(/11/);
      expect(out).toMatch(/5\/1|May 1/);
    });

    it('accepts a Date object', () => {
      const out = formatInstant(
        new Date('2026-05-01T15:00:00Z'),
        { hour: 'numeric', minute: '2-digit' },
        'America/New_York',
      );
      expect(out).toMatch(/11/);
    });
  });

  describe('getEmployeeTimezone', () => {
    it('falls back to the browser tz when no override is provided', () => {
      const tz = getEmployeeTimezone();
      // Whatever the test runner reports -- assert it's a non-empty IANA-shaped string.
      expect(tz).toMatch(/^[A-Z][A-Za-z_]+(\/[A-Za-z_]+)*$/);
    });

    it('returns the explicit override when given', () => {
      expect(getEmployeeTimezone('Europe/Berlin')).toBe('Europe/Berlin');
    });
  });

  describe('currentWeekStart', () => {
    // Anchor: Mon 2026-04-27 00:00 UTC. Walking each weekday forward should
    // resolve back to the same Monday until Sunday wraps and stays in week.
    it('returns the same Monday when called Monday in UTC', () => {
      expect(currentWeekStart(new Date('2026-04-27T12:00:00Z'), 'UTC')).toBe('2026-04-27');
    });

    it('returns the prior Monday when called Tuesday through Sunday in UTC', () => {
      expect(currentWeekStart(new Date('2026-04-28T12:00:00Z'), 'UTC')).toBe('2026-04-27'); // Tue
      expect(currentWeekStart(new Date('2026-04-29T12:00:00Z'), 'UTC')).toBe('2026-04-27'); // Wed
      expect(currentWeekStart(new Date('2026-04-30T12:00:00Z'), 'UTC')).toBe('2026-04-27'); // Thu
      expect(currentWeekStart(new Date('2026-05-01T12:00:00Z'), 'UTC')).toBe('2026-04-27'); // Fri
      expect(currentWeekStart(new Date('2026-05-02T12:00:00Z'), 'UTC')).toBe('2026-04-27'); // Sat
      expect(currentWeekStart(new Date('2026-05-03T12:00:00Z'), 'UTC')).toBe('2026-04-27'); // Sun
    });

    it('rolls into the next week on the following Monday', () => {
      expect(currentWeekStart(new Date('2026-05-04T00:00:01Z'), 'UTC')).toBe('2026-05-04');
    });

    it('respects the IANA tz when computing local-day weekday', () => {
      // 2026-04-27T03:00:00Z is still Sun 2026-04-26 23:00 EDT (UTC-04:00 in
      // late April). Asking in America/New_York should return the prior week's
      // Monday (2026-04-20), not the calendar-UTC Monday.
      expect(currentWeekStart(new Date('2026-04-27T03:00:00Z'), 'America/New_York')).toBe(
        '2026-04-20',
      );
    });

    it('handles the DST spring-forward week without skewing the Monday', () => {
      // Sun 2026-03-08 is the spring-forward day in America/New_York. Calling at
      // noon local that day must still resolve to the week-start Mon 2026-03-02.
      expect(currentWeekStart(new Date('2026-03-08T16:00:00Z'), 'America/New_York')).toBe(
        '2026-03-02',
      );
    });
  });
});
