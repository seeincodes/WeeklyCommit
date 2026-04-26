import { Routes, Route } from 'react-router-dom';
import { Card } from 'flowbite-react';

/**
 * Top-level federated component exposed to the PA host as
 * `weekly_commit/WeeklyCommitModule`. The host mounts this somewhere
 * inside its own <BrowserRouter> -- we use relative <Routes> so the
 * host's router context resolves the paths.
 *
 * Real routes (current/history/team/admin) ship in groups 11-12. v1
 * placeholder just confirms the federation handshake works.
 */
export function WeeklyCommitModule() {
  return (
    <Routes>
      <Route path="weekly-commit/*" element={<Placeholder />} />
    </Routes>
  );
}

function Placeholder() {
  return (
    <div data-testid="weekly-commit-root" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-md">
        <h1 className="text-2xl font-bold text-gray-900">Weekly Commit</h1>
        <p className="text-gray-600">Module loaded. Routes ship in groups 11-12.</p>
        <p className="text-sm text-gray-400" data-testid="version">
          Build: {__WC_GIT_SHA__}
        </p>
      </Card>
    </div>
  );
}
