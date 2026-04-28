import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { WeekContextBadge } from './WeekContextBadge';

// Pure presentational component with two inputs (weekStart, tz) and no
// router / store dependencies. Tests pin the date-formatting contract:
// same-year span renders as `Apr 27 – May 3`, year-straddle renders with
// the year on each side. UTC is the source of truth -- tz only affects
// month/day formatting via Intl.DateTimeFormat.

describe('<WeekContextBadge />', () => {
  it('renders the week-of label and a Mon-Sun span', () => {
    // Use tz='UTC' so the formatted month/day matches the parsed UTC date
    // exactly. Non-UTC zones (e.g. Pacific) can shift the formatted day
    // relative to the input weekStart, since the component parses as
    // UTC-midnight and then formats through Intl.DateTimeFormat in the
    // requested tz. The test pins format, not tz arithmetic.
    render(<WeekContextBadge weekStart="2026-04-27" tz="UTC" />);
    expect(screen.getByTestId('week-context-badge')).toBeInTheDocument();
    expect(screen.getByText(/week of/i)).toBeInTheDocument();
    expect(screen.getByText(/Apr 27.*May 3/)).toBeInTheDocument();
  });

  it('omits the year when start and end fall in the same calendar year', () => {
    render(<WeekContextBadge weekStart="2026-06-01" tz="UTC" />);
    // No 4-digit year token in the formatted span.
    expect(screen.getByTestId('week-context-badge').textContent).not.toMatch(/\b20\d{2}\b/);
  });

  it('shows both years when the span straddles a year boundary', () => {
    // 2026-12-28 (Mon) → 2027-01-03 (Sun).
    render(<WeekContextBadge weekStart="2026-12-28" tz="UTC" />);
    const text = screen.getByTestId('week-context-badge').textContent ?? '';
    expect(text).toMatch(/2026/);
    expect(text).toMatch(/2027/);
  });

  it('throws on a too-short weekStart (hits the explicit guard)', () => {
    // `2026` splits into ['2026'], so y is set but m and d are null --
    // the explicit `Invalid weekStart` guard fires before Intl gets the
    // value. Pinning the message confirms the guard runs (otherwise the
    // component would still throw, but from Intl with a vaguer message).
    expect(() => render(<WeekContextBadge weekStart="2026" tz="UTC" />)).toThrow(
      /Invalid weekStart/,
    );
  });

  it('throws (any error) on a non-numeric weekStart', () => {
    // `not-a-date` splits into 3 non-null tokens that all parse to NaN, so
    // the explicit guard misses; Intl raises `Invalid time value` further
    // down. We assert *some* error, not a specific message -- the test's
    // contract is "don't render NaN - NaN silently," not which layer
    // catches it.
    expect(() => render(<WeekContextBadge weekStart="not-a-date" tz="UTC" />)).toThrow();
  });
});
