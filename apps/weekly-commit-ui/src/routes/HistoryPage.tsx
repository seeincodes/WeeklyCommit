import { AppShell } from '../components/AppShell';

/**
 * Route shell for /weekly-commit/history. The historical-weeks list ships
 * later in group 11; this shell renders a minimal placeholder so the
 * routing layer has something visible (Playwright `toBeVisible` requires
 * non-zero dimensions, so an empty div would fail the smoke test).
 */
export function HistoryPage() {
  return (
    <AppShell testId="history-page" eyebrow="Past weeks" title="History">
      <div className="rounded-lg border border-dashed border-slate-300 bg-white p-10 text-center text-slate-500">
        Historical weeks list lands later in group 11.
      </div>
    </AppShell>
  );
}
