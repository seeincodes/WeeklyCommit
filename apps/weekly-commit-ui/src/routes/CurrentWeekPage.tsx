import { AppShell } from '../components/AppShell';
import { WeekContextBadge } from '../components/WeekContextBadge';
import { WeekEditor } from '../components/WeekEditor';
import { currentWeekStart, getEmployeeTimezone } from '../lib/timezone';

/**
 * Route shell for /weekly-commit/current. The state-aware <WeekEditor />
 * owns the plan fetch and mode routing; this page wraps it with the
 * AppShell header (which carries the product nav, eyebrow, and the
 * version stamp inside the footer the Playwright smoke test grep'd for).
 *
 * Title text remains "Weekly Commit" so the smoke test's heading regex
 * `getByRole('heading', { name: /weekly commit/i })` continues to match
 * inside the `current-week-page` test-id container.
 */
export function CurrentWeekPage() {
  const now = new Date();
  const tz = getEmployeeTimezone();
  const weekStart = currentWeekStart(now, tz);
  return (
    <AppShell
      testId="current-week-page"
      eyebrow="This week"
      title="Weekly Commit"
      headerSlot={<WeekContextBadge weekStart={weekStart} tz={tz} />}
    >
      <WeekEditor now={now} />
    </AppShell>
  );
}
