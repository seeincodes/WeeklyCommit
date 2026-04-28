import { ChevronRightIcon } from '../components/icons';

interface DemoModeBannerProps {
  /**
   * The active demo role, resolved from the URL's `?devRole=` param at boot.
   * Passed in (rather than re-resolved here) so a single source of truth at
   * the boot site governs both this banner and the devAuth shim.
   */
  role: 'MANAGER' | 'IC' | 'IC_NULL_MANAGER' | 'ADMIN';
}

const DISPLAY_NAME: Record<DemoModeBannerProps['role'], string> = {
  MANAGER: 'Ada Lovelace · Manager',
  IC: 'Ben Carter · IC',
  IC_NULL_MANAGER: 'Frankie Hopper · IC (no manager)',
  ADMIN: 'Site Admin · Admin',
};

/**
 * Thin always-visible strip at the top of every page, only rendered in
 * demo-mode builds. Tells the viewer they're in a demo, who they're acting
 * as, and gives a one-click "switch" link back to the {@link
 * DemoLoginPicker}.
 *
 * Lives outside the route's AppShell so it's invariant across navigation.
 * Sized small enough that it doesn't compete with the AppShell's product
 * header for attention.
 */
export function DemoModeBanner({ role }: DemoModeBannerProps) {
  const handleSwitch = () => {
    // Drop the devRole param so the next boot lands on DemoLoginPicker.
    const url = new URL(window.location.href);
    url.searchParams.delete('devRole');
    window.location.assign(url.toString());
  };

  return (
    <div
      data-testid="demo-mode-banner"
      role="status"
      aria-label="Demo mode notice"
      className="flex items-center justify-between gap-3 border-b border-amber-200 bg-amber-50 px-4 py-1.5 text-xs text-amber-900"
    >
      <span className="flex items-center gap-2">
        <span className="font-semibold uppercase tracking-wide text-amber-700">Demo</span>
        <span className="hidden sm:inline">Viewing as</span>
        <span className="font-medium" data-testid="demo-mode-banner-identity">
          {DISPLAY_NAME[role]}
        </span>
      </span>
      <button
        type="button"
        onClick={handleSwitch}
        data-testid="demo-mode-banner-switch"
        className="inline-flex items-center gap-1 rounded-md px-2 py-0.5 font-medium text-amber-700 transition-colors hover:bg-amber-100 focus:outline-none focus:ring-2 focus:ring-amber-400"
      >
        Switch identity
        <ChevronRightIcon className="h-3 w-3" />
      </button>
    </div>
  );
}
