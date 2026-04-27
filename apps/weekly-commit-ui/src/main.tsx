// Standalone (dev-server / Playwright) entry only.
//
// In production-federation mode the PA host imports `WeeklyCommitModule`
// directly via `weekly_commit/WeeklyCommitModule` and brings its own
// router + Sentry context. This file is the OTHER path: `vite dev` and
// the Playwright remote-in-isolation smoke (group 9 subtask 6).
//
// Sentry init is gated on VITE_SENTRY_DSN so we don't double-init when
// the host has already booted Sentry. In standalone mode the env var is
// typically unset and Sentry stays inert.

import './index.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import * as Sentry from '@sentry/react';
import { Flowbite } from 'flowbite-react';
import { WeeklyCommitModule } from './WeeklyCommitModule';

const sentryDsn = import.meta.env.VITE_SENTRY_DSN;
if (sentryDsn) {
  Sentry.init({
    dsn: sentryDsn,
    release: __WC_GIT_SHA__,
    environment: import.meta.env.MODE,
    integrations: [Sentry.browserTracingIntegration()],
    tracesSampleRate: 0.1,
  });
}

// Standalone-dev only: mint a self-signed JWT and patch fetch so the e2e-profile
// backend accepts our requests. The federated path (host imports WeeklyCommitModule
// directly) skips this entire file. The DEV gate ensures `vite build` tree-shakes
// the dev/devAuth module + its `jose` + private-key payload out of the prod bundle.
async function boot(): Promise<void> {
  if (import.meta.env.DEV) {
    // The dev auth shim is best-effort: it lets a developer hit the backend
    // from `vite dev` against the e2e Spring profile without 401s. If it
    // fails (PEM not found, jose can't parse, anything else), log and move on
    // -- the app still mounts and the developer sees backend 401s if they hit
    // the real API. Critically, this means Playwright's smoke (which doesn't
    // need backend auth at all) is unaffected by dev-auth setup errors.
    try {
      const { installDevAuth } = await import('./dev/devAuth');
      await installDevAuth();
    } catch (err) {
      console.warn('[dev-auth] init failed; continuing without backend auth:', err);
    }
  }

  const rootEl = document.getElementById('root');
  if (!rootEl) throw new Error('#root not found');

  createRoot(rootEl).render(
    <StrictMode>
      <Flowbite>
        <HashRouter>
          {/*
            Standalone-only redirect: WeeklyCommitModule's routes are all under
            /weekly-commit/*, matching how the PA host mounts the federated
            remote. In standalone mode the HashRouter starts at "/" which the
            module doesn't recognize, so we send "/" → "/weekly-commit/current"
            here. The catch-all "*" hands every other path to the module so its
            existing route tree (current, history, team, team/:employeeId)
            continues to work.
          */}
          <Routes>
            <Route path="/" element={<Navigate to="/weekly-commit/current" replace />} />
            <Route path="*" element={<WeeklyCommitModule />} />
          </Routes>
        </HashRouter>
      </Flowbite>
    </StrictMode>,
  );
}

void boot();
