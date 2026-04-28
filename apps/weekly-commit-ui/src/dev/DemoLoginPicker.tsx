import { SparkleIcon } from '../components/icons';

/**
 * Demo-mode landing page. Renders when the standalone-dev / demo-deploy build
 * boots without a `?devRole=` URL param, in place of the application itself.
 * The user picks a seeded identity; clicking sets the `devRole` query param
 * and reloads, which the existing `devAuth` shim picks up at the next boot.
 *
 * Three reasons this exists rather than auto-defaulting to MANAGER:
 *
 *   1. Stakeholder-friendly entry point. "Open the URL, click an identity"
 *      reads better than "open the URL, learn about query params, edit the
 *      URL, hit enter."
 *   2. Honest demo framing. The banner explicitly says "no real auth";
 *      previously the only signal a viewer had was the `[dev:MANAGER]`
 *      suffix that devAuth pokes into document.title.
 *   3. Friction-free identity switching. The accompanying `DemoModeBanner`
 *      links back here so a viewer can hop between IC and Manager views
 *      without manually rewriting the URL.
 *
 * Profile-gated to demo-mode builds only -- the federated production path
 * (real Auth0 + the PA host) never instantiates this component because
 * `main.tsx` checks `VITE_DEMO_MODE === 'true'` before rendering.
 */
export function DemoLoginPicker() {
  return (
    <div
      data-testid="demo-login-picker"
      className="motion-safe:animate-fade-in-up flex min-h-screen flex-col items-center justify-center bg-slate-50 px-6 py-12"
    >
      <div className="w-full max-w-2xl rounded-2xl border border-slate-200 bg-white p-8 shadow-soft sm:p-10">
        <header className="flex flex-col items-center gap-3 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-soft text-brand">
            <SparkleIcon className="h-6 w-6" />
          </span>
          <h1 className="text-display text-slate-900">Weekly Commit demo</h1>
          <p className="max-w-md text-sm text-slate-600">
            Pick an identity to explore the product. This is a demo build with no real
            authentication — anyone can switch identity at any time. None of the data here is real.
          </p>
        </header>

        <ul className="mt-8 grid grid-cols-1 gap-3 sm:grid-cols-2" data-testid="demo-login-roles">
          {ROLE_CARDS.map((card) => (
            <li key={card.role}>
              <RoleCard {...card} />
            </li>
          ))}
        </ul>

        <p className="mt-6 text-center text-meta uppercase text-slate-400">
          Demo data: 1 manager · 3 ICs · 1 unassigned · current week + 1 prior reconciled week
        </p>
      </div>
    </div>
  );
}

interface RoleCardProps {
  role: 'MANAGER' | 'IC' | 'IC_NULL_MANAGER' | 'ADMIN';
  name: string;
  title: string;
  description: string;
}

const ROLE_CARDS: RoleCardProps[] = [
  {
    role: 'MANAGER',
    name: 'Ada Lovelace',
    title: 'Manager',
    description: 'See the team rollup, drill into each IC’s plan, leave review comments.',
  },
  {
    role: 'IC',
    name: 'Ben Carter',
    title: 'IC reporting to Ada',
    description: 'Plan a current week, lock it, reconcile, carry forward missed work.',
  },
  {
    role: 'IC_NULL_MANAGER',
    name: 'Frankie Hopper',
    title: 'IC, no manager',
    description: 'See how the IC surface behaves when the JWT has `manager_id: null`.',
  },
  {
    role: 'ADMIN',
    name: 'Site Admin',
    title: 'Admin',
    description: 'Hit the admin endpoints (DLT replay, unassigned-employees report).',
  },
];

function RoleCard({ role, name, title, description }: RoleCardProps) {
  const handleClick = () => {
    // Set the `devRole` param on the *real* search string (not the hash).
    // HashRouter routes live after `#`; query params for boot-time reads
    // must go on `window.location.search` so the existing devAuth shim's
    // `new URLSearchParams(window.location.search)` picks them up.
    const url = new URL(window.location.href);
    url.searchParams.set('devRole', role);
    window.location.assign(url.toString());
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      data-testid={`demo-login-role-${role}`}
      className="group flex h-full w-full flex-col items-start gap-2 rounded-xl border border-slate-200 bg-white p-4 text-left transition-all hover:-translate-y-0.5 hover:border-brand/40 hover:shadow-soft focus:outline-none focus:ring-2 focus:ring-brand/40"
    >
      <span className="text-meta uppercase text-slate-500">{title}</span>
      <span className="text-base font-semibold text-slate-900">{name}</span>
      <span className="text-sm text-slate-600">{description}</span>
      <span className="mt-auto self-end text-xs font-medium text-brand opacity-0 transition-opacity group-hover:opacity-100">
        Sign in →
      </span>
    </button>
  );
}
