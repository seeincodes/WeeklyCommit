import { useEffect } from 'react';
// Subpath import to avoid pulling the full flowbite-react barrel; see
// the explanation in src/main.tsx where the same pattern is applied.
import { Toast, ToastToggle } from 'flowbite-react/components/Toast';
import { conflictToastActions, selectConflictToast } from '@wc/rtk-api-client';
import { useAppDispatch, useAppSelector } from '../store/typedHooks';

const DEFAULT_AUTO_DISMISS_MS = 5_000;

/**
 * Per-code copy. Falls back to the generic message for any code we don't
 * have a tailored line for. Add entries here as the backend introduces new
 * 409 reasons; today every 409 maps to OptimisticLockException via the
 * GlobalExceptionHandler so only the optimistic-lock copy is exercised.
 */
const COPY: Record<string, { headline: string; detail: string }> = {
  CONFLICT_OPTIMISTIC_LOCK: {
    headline: 'Refreshed in the background',
    detail: 'Another tab updated this plan. We just pulled the latest and retried.',
  },
};

const FALLBACK_COPY = {
  headline: 'Refreshed in the background',
  detail: 'Something changed under us. We pulled the latest and retried.',
};

interface ConflictToastProps {
  /** Override the auto-dismiss window (ms). Tests pass 0 to disable. */
  autoDismissMs?: number;
}

/**
 * Bottom-right slide-in toast that surfaces 409 conflicts caught by
 * `withConflictRetry`. The retry has already happened by the time we render
 * -- the toast is purely a "heads up, we just did some magic" notification
 * so the user understands why their last action took a beat longer.
 *
 * Mounted once per top-level route surface (WeekEditor pages + team
 * routes); the Redux slice in @wc/rtk-api-client is the single source of
 * truth so multiple mount points stay in sync.
 *
 * Honors `prefers-reduced-motion`: the slide-in transition is gated by
 * Tailwind's `motion-safe:` variant so vestibular-sensitive users get the
 * toast without the slide.
 */
export function ConflictToast({
  autoDismissMs = DEFAULT_AUTO_DISMISS_MS,
}: ConflictToastProps = {}) {
  const dispatch = useAppDispatch();
  const { visible, code } = useAppSelector(selectConflictToast);

  useEffect(() => {
    if (!visible || autoDismissMs <= 0) return undefined;
    const handle = setTimeout(() => dispatch(conflictToastActions.hide()), autoDismissMs);
    return () => clearTimeout(handle);
  }, [visible, code, autoDismissMs, dispatch]);

  if (!visible) return null;

  // Lookup-then-default. The COPY map only carries codes we have tailored
  // copy for; everything else (including missing/null code) falls through.
  const mapped = code != null ? COPY[code] : undefined;
  const { headline, detail } = mapped ?? FALLBACK_COPY;

  return (
    <div
      data-testid="conflict-toast"
      className="pointer-events-none fixed bottom-6 right-6 z-50 flex motion-safe:animate-slide-in-right"
      role="status"
      aria-live="polite"
    >
      <Toast className="pointer-events-auto max-w-sm shadow-lg">
        <div
          aria-hidden="true"
          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-600"
        >
          {/* Inline SVG so we don't pull a whole icon library for one glyph. */}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="h-5 w-5"
          >
            <path d="M21 12a9 9 0 1 1-3-6.7" />
            <path d="M21 4v5h-5" />
          </svg>
        </div>
        <div className="ml-3 flex flex-col gap-0.5 text-sm">
          <span data-testid="conflict-toast-headline" className="font-semibold text-gray-900">
            {headline}
          </span>
          <span data-testid="conflict-toast-detail" className="text-gray-700">
            {detail}
          </span>
        </div>
        <ToastToggle data-testid="conflict-toast-dismiss" />
      </Toast>
    </div>
  );
}
