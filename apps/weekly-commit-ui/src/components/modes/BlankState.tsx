import { useCreateCurrentForMeMutation } from '@wc/rtk-api-client';
import { ArrowRightIcon, PebbleIcon, RockIcon, SandIcon, SparkleIcon } from '../icons';

/**
 * Empty-state pane for the WeekEditor when no plan exists for the current week
 * (the GET /plans/me/current endpoint returned 404). Per MEMO #10 + USER_FLOW
 * flow 1, plan creation is *explicit* -- a button click, not a side-effect of
 * route navigation.
 *
 * Clicking "Create plan" fires POST /plans; RTK Query invalidates the Plan
 * LIST tag, useGetCurrentForMeQuery refetches, and WeekEditor flips into
 * DraftMode without any local state plumbing.
 *
 * Visual model: a centred illustrative cluster (the chess-tier glyphs in
 * tier colours) sits above the headline + CTA so the empty surface reads
 * as a deliberate first-step rather than a "loading failed" placeholder.
 * The previous treatment was a single line "You haven't started this week
 * yet." with a flat blue rectangle button -- functionally fine, visually
 * the most "lackluster" surface in the product.
 */
export function BlankState() {
  const [createPlan, { isLoading, error }] = useCreateCurrentForMeMutation();

  return (
    <div
      data-testid="week-editor-blank"
      className="motion-safe:animate-fade-in-up flex flex-col items-center gap-6 rounded-2xl border border-slate-200 bg-white px-6 py-12 text-center shadow-soft-sm"
    >
      <Illustration />
      <div className="flex flex-col items-center gap-2">
        <h2 className="text-title text-slate-900">Ready to plan your week?</h2>
        <p className="max-w-md text-sm text-slate-600">
          Pick a Top Rock, line up the rest of your commits, and lock the week when you’re happy.
          You can carry anything you don’t finish into next week.
        </p>
      </div>
      <button
        type="button"
        onClick={() => void createPlan()}
        disabled={isLoading}
        className="inline-flex items-center gap-2 rounded-md bg-brand px-5 py-2.5 text-sm font-semibold text-white shadow-soft transition-colors hover:bg-brand-hover focus:outline-none focus:ring-2 focus:ring-brand/40 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none"
      >
        <SparkleIcon className="h-4 w-4" />
        {isLoading ? 'Creating…' : 'Create plan'}
        {!isLoading && <ArrowRightIcon className="h-4 w-4" />}
      </button>
      {error && (
        <div
          data-testid="blank-state-error"
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
        >
          Couldn’t create the plan. Try again.
        </div>
      )}
    </div>
  );
}

/**
 * Stylised tier-glyph cluster: Rock anchored top-centre, Pebble + Sand
 * orbiting beneath. Positions are absolute inside a sized box so the
 * shapes overlap deliberately -- reads as a cohesive emblem rather than
 * three separate icons. Pure decorative; aria-hidden so screen readers
 * skip it.
 */
function Illustration() {
  return (
    <div
      aria-hidden
      className="relative flex h-28 w-32 items-center justify-center"
      data-testid="blank-state-illustration"
    >
      <span className="absolute left-1/2 top-0 -translate-x-1/2 text-rock-ink">
        <RockIcon className="h-16 w-16 drop-shadow-sm" />
      </span>
      <span className="absolute bottom-2 left-2 text-pebble-ink">
        <PebbleIcon className="h-12 w-12 opacity-90 drop-shadow-sm" />
      </span>
      <span className="absolute bottom-1 right-2 text-sand-ink">
        <SandIcon className="h-12 w-12 opacity-90 drop-shadow-sm" />
      </span>
    </div>
  );
}
