import { Card } from 'flowbite-react';

/**
 * Route shell for /weekly-commit/team. The state-aware <TeamRollup /> lands
 * inside this page in subtask 2; for now this shell renders the heading so
 * the routing layer has visible content (Playwright's `toBeVisible` requires
 * non-zero dimensions, same lesson learned in group 11).
 */
export function TeamPage() {
  return (
    <div data-testid="team-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-5xl">
        <h1 className="text-2xl font-bold text-gray-900">Team rollup</h1>
        <p className="text-gray-600">Team rollup lands in subtask 2.</p>
      </Card>
    </div>
  );
}
