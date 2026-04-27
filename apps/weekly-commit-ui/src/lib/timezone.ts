/**
 * Timezone helpers for the weekly-commit UI.
 *
 * Per [MVP21] + MEMO "TZ / DST edges": all week math at the service layer is
 * UTC; the UI converts using `Intl.DateTimeFormat` with the employee's IANA
 * timezone. v1 sources the TZ from the browser's resolved IANA -- the JWT
 * `timezone` claim integration lands with the Auth0 wiring (out of group 11
 * scope; see TODO below).
 */

export const RECONCILE_OFFSET_DAYS = 4;

/**
 * Resolves the employee's IANA timezone. v1 fallback is the browser's
 * resolved TZ; an explicit override (e.g. from the JWT `timezone` claim)
 * wins when provided.
 *
 * TODO(group-?-auth-integration): wire JWT `timezone` claim through a
 * React context provider so this no longer needs the browser fallback.
 */
export function getEmployeeTimezone(override?: string): string {
  if (override != null && override !== '') return override;
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/**
 * Returns the UTC instant corresponding to "midnight at start of `dateOnly`"
 * (the date interpreted in the given timezone). Walks the IANA offset via
 * `Intl.DateTimeFormat.formatToParts` -- handles DST without external deps.
 *
 * `dateOnly` is a `YYYY-MM-DD` string; the returned Date is the moment that
 * displays as `${dateOnly} 00:00:00` when formatted in `tz`.
 */
function midnightInTzAsUtc(dateOnly: string, tz: string): Date {
  const [year, month, day] = dateOnly.split('-').map((s) => parseInt(s, 10));
  if (year == null || month == null || day == null) {
    throw new Error(`Invalid date string: ${dateOnly}`);
  }

  // First guess: treat the components as UTC. The actual UTC instant for
  // "midnight in tz" differs by the tz's offset at that wall-clock moment.
  // Computing the offset by formatting the guess gives us the correction;
  // applying it once is enough because IANA offsets only change at DST
  // boundaries, and a 1-day-wide guess never straddles one.
  const guessUtc = Date.UTC(year, month - 1, day, 0, 0, 0);
  const offsetMs = tzOffsetMs(new Date(guessUtc), tz);
  return new Date(guessUtc - offsetMs);
}

/**
 * Returns the offset (ms) between UTC and the given timezone at the given
 * instant. Positive when `tz` is east of UTC.
 *
 * Uses `formatToParts` to extract the wall-clock representation in `tz`,
 * rebuilds it as a UTC instant, and takes the diff. The standard recipe.
 */
function tzOffsetMs(at: Date, tz: string): number {
  const dtf = new Intl.DateTimeFormat('en-US', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  const parts = dtf.formatToParts(at).reduce<Record<string, string>>((acc, p) => {
    if (p.type !== 'literal') acc[p.type] = p.value;
    return acc;
  }, {});
  // Intl emits hour='24' at midnight in some locales; normalize to 0.
  const hour = parts.hour === '24' ? '00' : parts.hour;
  const wallUtcMs = Date.UTC(
    parseInt(parts.year!, 10),
    parseInt(parts.month!, 10) - 1,
    parseInt(parts.day!, 10),
    parseInt(hour!, 10),
    parseInt(parts.minute!, 10),
    parseInt(parts.second!, 10),
  );
  return wallUtcMs - at.getTime();
}

/**
 * True when `now` is at or after the reconciliation threshold for a plan
 * whose `weekStart` falls in the given IANA timezone. The threshold is
 * `weekStart + 4 days, 00:00:00 local`.
 */
export function isReconcileEligible(weekStart: string, now: Date, tz: string): boolean {
  const weekStartLocalMidnight = midnightInTzAsUtc(weekStart, tz);
  const thresholdMs =
    weekStartLocalMidnight.getTime() + RECONCILE_OFFSET_DAYS * 24 * 60 * 60 * 1000;
  return now.getTime() >= thresholdMs;
}

/**
 * Formats a UTC instant for display in the given timezone using
 * `Intl.DateTimeFormat`. Pure pass-through to the platform formatter.
 */
export function formatInstant(
  instant: string | Date,
  options: Intl.DateTimeFormatOptions,
  tz: string,
): string {
  const date = typeof instant === 'string' ? new Date(instant) : instant;
  return new Intl.DateTimeFormat('en-US', { ...options, timeZone: tz }).format(date);
}
