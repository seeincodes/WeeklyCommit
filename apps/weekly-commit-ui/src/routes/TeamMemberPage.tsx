import { useNavigate, useParams } from 'react-router-dom';
import { Card } from 'flowbite-react';
import { IcDrawer } from '../components/IcDrawer';
import { getEmployeeTimezone } from '../lib/timezone';

/**
 * Returns the YYYY-MM-DD of the Monday on or before "today" in the employee's
 * timezone. Mirrors the backend's UTC week-start derivation -- ISO weeks start
 * Monday per [MVP21]. TODO(group-?-week-helper): consolidate with the duplicate
 * in TeamPage once the cross-cutting helper lands in lib/timezone.ts.
 */
function currentWeekStart(): string {
  const tz = getEmployeeTimezone();
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  });
  const parts = fmt.formatToParts(new Date());
  const yyyy = parts.find((p) => p.type === 'year')!.value;
  const mm = parts.find((p) => p.type === 'month')!.value;
  const dd = parts.find((p) => p.type === 'day')!.value;
  const weekday = parts.find((p) => p.type === 'weekday')!.value;
  const offsetByWeekday: Record<string, number> = {
    Mon: 0,
    Tue: 1,
    Wed: 2,
    Thu: 3,
    Fri: 4,
    Sat: 5,
    Sun: 6,
  };
  const offset = offsetByWeekday[weekday] ?? 0;
  const d = new Date(`${yyyy}-${mm}-${dd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - offset);
  return d.toISOString().slice(0, 10);
}

/**
 * Route shell for /weekly-commit/team/:employeeId -- the addressable
 * single-member view. Same `<IcDrawer />` as TeamPage's overlay, but the
 * close action navigates back to /weekly-commit/team rather than just
 * clearing a search-param (preserves browser-back semantics for both the
 * "drilled in from rollup" and "deep-linked" entry paths).
 *
 * `employeeName` is unknown at this entry point (no rollup row to label
 * from), so we surface the id as the drawer heading. Once a profile-fetch
 * helper lands cross-route, swap in the real display name.
 */
export function TeamMemberPage() {
  const { employeeId } = useParams<{ employeeId: string }>();
  const navigate = useNavigate();

  if (!employeeId) {
    return (
      <div data-testid="team-member-page" className="p-6 bg-gray-50 min-h-screen">
        <Card className="max-w-3xl">
          <h1 className="text-2xl font-bold text-gray-900">Team member</h1>
          <p data-testid="team-member-missing" role="alert">
            No employee id in the URL.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div data-testid="team-member-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-3xl">
        <h1 className="text-2xl font-bold text-gray-900">Team member</h1>
        <p className="text-gray-600" data-testid="team-member-id">
          Viewing employee: {employeeId}
        </p>
      </Card>
      <IcDrawer
        employeeId={employeeId}
        employeeName={employeeId}
        weekStart={currentWeekStart()}
        onClose={() => navigate('/weekly-commit/team')}
      />
    </div>
  );
}
