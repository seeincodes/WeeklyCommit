import { Card } from 'flowbite-react';

/**
 * Route shell for /weekly-commit/current. The state-aware <WeekEditor />
 * lands here in subtask 2; for now this shell renders the page heading
 * and version stamp so smoke tests have a stable target while the real
 * surfaces are built out.
 */
export function CurrentWeekPage() {
  return (
    <div data-testid="current-week-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-md">
        <h1 className="text-2xl font-bold text-gray-900">Weekly Commit</h1>
        <p className="text-gray-600">Current week editor lands in subtask 2.</p>
        <p className="text-sm text-gray-400" data-testid="version">
          Build: {__WC_GIT_SHA__}
        </p>
      </Card>
    </div>
  );
}
