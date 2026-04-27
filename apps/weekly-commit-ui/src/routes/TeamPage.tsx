import { useSearchParams } from 'react-router-dom';
import { useGetTeamRollupQuery } from '@wc/rtk-api-client';
import { AppShell } from '../components/AppShell';
import { IcDrawer } from '../components/IcDrawer';
import { MemberCard } from '../components/MemberCard';
import { TeamRollup } from '../components/TeamRollup';
import { WeekContextBadge } from '../components/WeekContextBadge';
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
 *
 * Wraps the rollup in the shared AppShell so the manager surface inherits
 * the same product header / nav / footer as the IC surfaces. Header slot
 * carries the week-of badge for the rollup window.
 */
export function TeamPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const drawerEmployeeId = searchParams.get('employeeId');
  const tz = getEmployeeTimezone();
  const weekStart = currentWeekStart(new Date(), tz);

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
    <AppShell
      testId="team-page"
      eyebrow="Manager view"
      title="Team rollup"
      headerSlot={<WeekContextBadge weekStart={weekStart} tz={tz} />}
    >
      {isLoading && (
        <div data-testid="team-loading" className="text-slate-500">
          Loading…
        </div>
      )}
      {error && (
        <div
          data-testid="team-error"
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
        >
          Couldn’t load team rollup.
        </div>
      )}
      {rollup && (
        <TeamRollup
          rollup={rollup}
          renderMember={(m) => <MemberCard key={m.employeeId} member={m} onClick={openDrawer} />}
        />
      )}
      {drawerEmployeeId != null && (
        <IcDrawer
          employeeId={drawerEmployeeId}
          employeeName={drawerEmployeeName}
          weekStart={weekStart}
          onClose={closeDrawer}
        />
      )}
    </AppShell>
  );
}
