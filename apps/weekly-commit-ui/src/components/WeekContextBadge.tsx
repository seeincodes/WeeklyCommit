import { CalendarIcon } from './icons';

interface WeekContextBadgeProps {
  /** Monday of the displayed week as a `YYYY-MM-DD` string. */
  weekStart: string;
  /** IANA timezone the dates should be formatted in. */
  tz: string;
}

/**
 * Compact badge that surfaces "which week am I looking at?" in every
 * route's header. Renders the Mon-Sun span as `Apr 27 – May 3` (no
 * year unless the span crosses December). The icon prefix and small
 * pill shape distinguish this from the page title without competing
 * for the eye.
 *
 * Pure presentational; both inputs flow from `currentWeekStart` /
 * `getEmployeeTimezone` at the call site so the badge stays clock-
 * deterministic for tests.
 */
export function WeekContextBadge({ weekStart, tz }: WeekContextBadgeProps) {
  const start = parseDateOnly(weekStart);
  const end = addDays(start, 6);
  return (
    <div
      data-testid="week-context-badge"
      className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 shadow-soft-sm"
    >
      <CalendarIcon className="h-4 w-4 text-slate-400" aria-hidden />
      <span className="text-meta uppercase text-slate-500">Week of</span>
      <span className="font-medium text-slate-900">{formatRange(start, end, tz)}</span>
    </div>
  );
}

function parseDateOnly(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  if (y == null || m == null || d == null) {
    throw new Error(`Invalid weekStart: ${s}`);
  }
  return new Date(Date.UTC(y, m - 1, d));
}

function addDays(d: Date, n: number): Date {
  const next = new Date(d);
  next.setUTCDate(d.getUTCDate() + n);
  return next;
}

function formatRange(start: Date, end: Date, tz: string): string {
  const monthDay = (d: Date) =>
    new Intl.DateTimeFormat('en-US', { timeZone: tz, month: 'short', day: 'numeric' }).format(d);
  const sameYear = start.getUTCFullYear() === end.getUTCFullYear();
  const range = `${monthDay(start)} – ${monthDay(end)}`;
  // Year is implied 99% of the time; surface it only when the span straddles
  // a year boundary so the badge stays terse on the common path.
  if (sameYear) return range;
  const yearOf = (d: Date) =>
    new Intl.DateTimeFormat('en-US', { timeZone: tz, year: 'numeric' }).format(d);
  return `${monthDay(start)} ${yearOf(start)} – ${monthDay(end)} ${yearOf(end)}`;
}
