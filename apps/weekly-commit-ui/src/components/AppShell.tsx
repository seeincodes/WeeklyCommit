import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { CalendarIcon, FlagIcon, SparkleIcon } from './icons';

interface AppShellProps {
  /** Eyebrow line above the page title -- short context like "This week" or "Team rollup". */
  eyebrow?: string;
  /** Page title. Renders at display weight in the header band. */
  title: string;
  /** Right-aligned content slot in the header (state badge, week-of, etc.). */
  headerSlot?: ReactNode;
  /**
   * Page content. The shell handles outer padding, max-width, and the
   * surface background; content lives inside an unpadded container so
   * each surface controls its own internal rhythm.
   */
  children: ReactNode;
  /**
   * Extra content rendered just under the page heading and above the
   * children -- typically a sub-nav, filter row, or summary strip. Kept
   * as a separate slot so it can sit flush against the header's bottom
   * border without fighting the page-content gutter.
   */
  subnav?: ReactNode;
  /** Marker the routing tests grep for. Wired to the outer wrapper. */
  testId?: string;
}

/**
 * Shared chrome for every weekly-commit route. Replaces the per-route
 * `<div className="p-6 bg-gray-50 min-h-screen"> <Card>...</Card> </div>`
 * pattern with a single header band + nav + content area + footer
 * structure so the surface feels like one product instead of four
 * separate cards floating in gray.
 *
 * Three structural pieces:
 *
 *   - <header> : product mark, primary nav (Current week / History /
 *     Team), eyebrow + page title, and a right-aligned slot for
 *     route-specific status (week-of pill, state badge, etc.).
 *   - <main>   : content. The route owns the internal layout; the shell
 *     only provides the gutter + max-width + surface bg.
 *   - <footer> : the build-stamp, demoted from the page body to a
 *     near-invisible footer line. Production prod RUM still references
 *     __WC_GIT_SHA__ via Sentry's release tag (see main.tsx); this only
 *     changes where the build string is *visible* to humans.
 *
 * The shell does not own a Router context -- the host mounts this remote
 * inside its own <BrowserRouter>, and standalone-dev wraps it in a
 * <MemoryRouter> via main.tsx.
 */
export function AppShell({ eyebrow, title, headerSlot, subnav, children, testId }: AppShellProps) {
  return (
    <div data-testid={testId} className="flex min-h-screen flex-col bg-slate-50">
      <ProductHeader />
      <div className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-6 pb-5 pt-6">
          <div className="flex items-end justify-between gap-4">
            <div className="flex flex-col gap-1">
              {eyebrow != null && eyebrow !== '' && (
                <span className="text-meta uppercase text-slate-500">{eyebrow}</span>
              )}
              <h1 className="text-display text-slate-900">{title}</h1>
            </div>
            {headerSlot}
          </div>
          {subnav}
        </div>
      </div>
      <main className="flex-1">
        <div className="mx-auto w-full max-w-6xl px-6 py-6">{children}</div>
      </main>
      <BuildFooter />
    </div>
  );
}

function ProductHeader() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex w-full max-w-6xl items-center gap-6 px-6 py-3">
        <div className="flex items-center gap-2 text-slate-900">
          <SparkleIcon className="h-5 w-5 text-brand" aria-hidden />
          <span className="text-sm font-semibold tracking-tight">Weekly Commit</span>
        </div>
        <nav aria-label="Weekly Commit" className="flex items-center gap-1 text-sm">
          <ShellNavLink to="/weekly-commit/current" icon={<CalendarIcon className="h-4 w-4" />}>
            This week
          </ShellNavLink>
          <ShellNavLink to="/weekly-commit/history" icon={<CalendarIcon className="h-4 w-4" />}>
            History
          </ShellNavLink>
          <ShellNavLink to="/weekly-commit/team" icon={<FlagIcon className="h-4 w-4" />}>
            Team
          </ShellNavLink>
        </nav>
      </div>
    </header>
  );
}

function ShellNavLink({
  to,
  icon,
  children,
}: {
  to: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <NavLink
      to={to}
      end={false}
      className={({ isActive }) =>
        [
          'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 transition-colors',
          isActive
            ? 'bg-brand-soft text-brand-ink'
            : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
        ].join(' ')
      }
    >
      <span className="opacity-80">{icon}</span>
      <span>{children}</span>
    </NavLink>
  );
}

function BuildFooter() {
  return (
    <footer
      data-testid="app-shell-footer"
      className="border-t border-slate-200 bg-white px-6 py-3 text-center text-meta uppercase text-slate-400"
    >
      <span data-testid="version">Build: {__WC_GIT_SHA__}</span>
    </footer>
  );
}
