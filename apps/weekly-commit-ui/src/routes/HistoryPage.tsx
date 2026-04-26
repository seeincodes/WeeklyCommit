import { Card } from 'flowbite-react';

/**
 * Route shell for /weekly-commit/history. The historical-weeks list ships
 * later in group 11; this shell renders a minimal placeholder so the
 * routing layer has something visible (Playwright `toBeVisible` requires
 * non-zero dimensions, so an empty div would fail the smoke test).
 */
export function HistoryPage() {
  return (
    <div data-testid="history-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-3xl">
        <h1 className="text-2xl font-bold text-gray-900">History</h1>
        <p className="text-gray-600">Historical weeks list lands later in group 11.</p>
      </Card>
    </div>
  );
}
