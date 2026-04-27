import { useNavigate, useParams } from 'react-router-dom';
import { Card } from 'flowbite-react';
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
        weekStart={currentWeekStart(new Date(), getEmployeeTimezone())}
        onClose={() => navigate('/weekly-commit/team')}
      />
    </div>
  );
}
