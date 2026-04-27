import { useNavigate, useParams } from 'react-router-dom';
import { AppShell } from '../components/AppShell';
import { IcDrawer } from '../components/IcDrawer';
import { currentWeekStart, getEmployeeTimezone } from '../lib/timezone';

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
      <AppShell testId="team-member-page" eyebrow="Team detail" title="Team member">
        <p
          data-testid="team-member-missing"
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
        >
          No employee id in the URL.
        </p>
      </AppShell>
    );
  }

  return (
    <AppShell testId="team-member-page" eyebrow="Team detail" title="Team member">
      <p data-testid="team-member-id" className="text-slate-600">
        Viewing employee: {employeeId}
      </p>
      <IcDrawer
        employeeId={employeeId}
        employeeName={employeeId}
        weekStart={currentWeekStart(new Date(), getEmployeeTimezone())}
        onClose={() => navigate('/weekly-commit/team')}
      />
    </AppShell>
  );
}
