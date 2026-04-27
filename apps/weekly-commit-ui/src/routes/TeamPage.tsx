import { useSearchParams } from 'react-router-dom';
import { Card } from 'flowbite-react';
import { useGetTeamRollupQuery } from '@wc/rtk-api-client';
import { TeamRollup } from '../components/TeamRollup';
import { MemberCard } from '../components/MemberCard';
import { IcDrawer } from '../components/IcDrawer';
import { currentWeekStart, getEmployeeTimezone } from '../lib/timezone';

// TODO(group-?-auth-context): replace with JWT-derived managerId via React
// context once the Auth0 principal hook lands. The dev-shim MANAGER's `sub`
// is hardcoded so the rollup endpoint resolves during MVP click-tests.
const DEV_MANAGER_ID = '22222222-2222-2222-2222-222222222222';

/**
 * Route shell for /weekly-commit/team. Composes the manager team rollup
 * ([MVP9]) and an overlay <IcDrawer /> ([MVP10]) keyed off the URL search
 * param `?employeeId=...`. URL-as-source-of-truth keeps deep-linking and the
 * back button working without a Redux slice.
 */
export function TeamPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const drawerEmployeeId = searchParams.get('employeeId');
  const weekStart = currentWeekStart(new Date(), getEmployeeTimezone());

  const {
    data: rollup,
    isLoading,
    error,
  } = useGetTeamRollupQuery({ managerId: DEV_MANAGER_ID, weekStart });

  const openDrawer = (employeeId: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('employeeId', employeeId);
    setSearchParams(next);
  };

  const closeDrawer = () => {
    const next = new URLSearchParams(searchParams);
    next.delete('employeeId');
    setSearchParams(next);
  };

  // The drawer needs a display name so it can label the dialog without firing
  // a second fetch. We pull it from the rollup data we already have; if the
  // user deep-links before the rollup resolves, fall back to a placeholder
  // until the data arrives and the next render fills it in.
  const drawerEmployeeName =
    drawerEmployeeId != null
      ? (rollup?.members?.find((m) => m.employeeId === drawerEmployeeId)?.name ?? 'Team member')
      : '';

  return (
    <div data-testid="team-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-5xl">
        <h1 className="text-2xl font-bold text-gray-900">Team rollup</h1>
        {isLoading && <div data-testid="team-loading">Loading…</div>}
        {error && (
          <div data-testid="team-error" role="alert">
            Couldn’t load team rollup.
          </div>
        )}
        {rollup && (
          <TeamRollup
            rollup={rollup}
            renderMember={(m) => <MemberCard key={m.employeeId} member={m} onClick={openDrawer} />}
          />
        )}
      </Card>
      {drawerEmployeeId != null && (
        <IcDrawer
          employeeId={drawerEmployeeId}
          employeeName={drawerEmployeeName}
          weekStart={weekStart}
          onClose={closeDrawer}
        />
      )}
    </div>
  );
}
