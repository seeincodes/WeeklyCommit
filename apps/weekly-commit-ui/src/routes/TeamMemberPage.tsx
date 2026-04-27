import { useParams } from 'react-router-dom';
import { Card } from 'flowbite-react';

/**
 * Route shell for /weekly-commit/team/:employeeId. The IC drawer (subtask 4)
 * is the primary surface here, but we also render an addressable page so a
 * manager can deep-link to a single direct report's view.
 */
export function TeamMemberPage() {
  const { employeeId } = useParams<{ employeeId: string }>();
  return (
    <div data-testid="team-member-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-5xl">
        <h1 className="text-2xl font-bold text-gray-900">Team member</h1>
        <p className="text-gray-600" data-testid="team-member-id">
          Viewing employee: {employeeId ?? 'unknown'}
        </p>
        <p className="text-gray-600">Drawer + comment field land in subtasks 4-5.</p>
      </Card>
    </div>
  );
}
