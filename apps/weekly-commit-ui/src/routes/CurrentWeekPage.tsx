import { Card } from 'flowbite-react';
import { WeekEditor } from '../components/WeekEditor';

/**
 * Route shell for /weekly-commit/current. The state-aware <WeekEditor />
 * owns the plan fetch and mode routing; this page wraps it with the
 * heading and version stamp that smoke tests target.
 */
export function CurrentWeekPage() {
  return (
    <div data-testid="current-week-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-3xl">
        <h1 className="text-2xl font-bold text-gray-900">Weekly Commit</h1>
        <WeekEditor now={new Date()} />
        <p className="text-sm text-gray-400" data-testid="version">
          Build: {__WC_GIT_SHA__}
        </p>
      </Card>
    </div>
  );
}
