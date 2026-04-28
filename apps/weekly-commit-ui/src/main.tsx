// Standalone (dev-server / Playwright / Railway demo) entry only.
//
// In production-federation mode the PA host imports `WeeklyCommitModule`
// directly via `weekly_commit/WeeklyCommitModule` and brings its own
// router + Sentry context. This file is the OTHER path: `vite dev`,
// the Playwright remote-in-isolation smoke (group 9 subtask 6), and
// the Railway single-service deploy (MEMO #13).
//
// Sentry init is gated on VITE_SENTRY_DSN so we don't double-init when
// the host has already booted Sentry. In standalone mode the env var is
// typically unset and Sentry stays inert.

import './index.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import * as Sentry from '@sentry/react';
// Subpath import (vs `from 'flowbite-react'`) bypasses the package barrel,
// which re-exports every component (Button/Card/Modal/Sidebar/...) and
// drags in heavy peers (react-icons, @floating-ui). flowbite-react 0.10.2
// declares no `sideEffects: false`, so the bundler conservatively keeps
// the whole barrel even though we only consume one component. The
// `./components/*` subpath is part of the package's published `exports`
// map -- standard ESM, not a private path.
import { Flowbite } from 'flowbite-react/components/Flowbite';
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

type DemoRole = 'MANAGER' | 'IC' | 'IC_NULL_MANAGER' | 'ADMIN';

const VALID_ROLES: readonly DemoRole[] = ['MANAGER', 'IC', 'IC_NULL_MANAGER', 'ADMIN'];

function parseDemoRole(): DemoRole | null {
  const fromUrl = new URLSearchParams(window.location.search).get('devRole');
  if (fromUrl != null && (VALID_ROLES as readonly string[]).includes(fromUrl)) {
    return fromUrl as DemoRole;
  }
  // VITE_DEV_AUTH_ROLE is the existing legacy override knob from devAuth.ts;
  // honoring it here keeps the picker out of CI runs that pre-select a role
  // via env (Playwright smoke, Cypress + Cucumber).
  const fromEnv = import.meta.env.VITE_DEV_AUTH_ROLE;
  if (typeof fromEnv === 'string' && (VALID_ROLES as readonly string[]).includes(fromEnv)) {
    return fromEnv as DemoRole;
  }
  return null;
}

// Standalone-dev AND demo-deploy: mint a self-signed JWT and patch fetch so the
// e2e-profile backend accepts our requests. The federated path (host imports
// WeeklyCommitModule directly) skips this entire file.
//
// Two activation paths:
//   - `import.meta.env.DEV`: standalone vite dev, the original use case.
//   - `import.meta.env.VITE_DEMO_MODE === 'true'`: production build with the
//     demo flag set. Used by docker-compose.demo.yml + the Railway deploy
//     (MEMO #13) so a static bundle can talk to a demo-profile backend.
//
// When neither flag is true `vite build` tree-shakes the dev/devAuth module +
// its `jose` + private-key payload out of the prod bundle. Both gates collapse
// to compile-time constants so the conditional branch is dead-code-eliminated.
async function boot(): Promise<void> {
  const demoMode = import.meta.env.VITE_DEMO_MODE === 'true';
  const isDevOrDemo = import.meta.env.DEV || demoMode;
  const role = parseDemoRole();

  const rootEl = document.getElementById('root');
  if (!rootEl) throw new Error('#root not found');
  const root = createRoot(rootEl);

  // Demo-mode landing page: when the user opens the URL bare (no `?devRole=`),
  // render an identity picker instead of silently auto-selecting MANAGER. This
  // is visible only in demo-mode builds; the federated production path never
  // reaches main.tsx at all, and standalone-dev ALSO skips the picker if
  // VITE_DEV_AUTH_ROLE is set (which Playwright + Cypress runners do).
  if (demoMode && role == null) {
    const { DemoLoginPicker } = await import('./dev/DemoLoginPicker');
    root.render(
      <StrictMode>
        <Flowbite>
          <DemoLoginPicker />
        </Flowbite>
      </StrictMode>,
    );
    return;
  }

  if (isDevOrDemo) {
    // The dev auth shim is best-effort: it lets a developer or demo-deploy
    // user hit the backend against the e2e Spring profile without 401s. If
    // it fails (PEM not found, jose can't parse, anything else), log and
    // move on -- the app still mounts and the user sees backend 401s if
    // they hit the real API. Critically, this means Playwright's smoke
    // (which doesn't need backend auth at all) is unaffected by dev-auth
    // setup errors.
    try {
      const { installDevAuth } = await import('./dev/devAuth');
      await installDevAuth();
    } catch (err) {
      console.warn('[dev-auth] init failed; continuing without backend auth:', err);
    }
  }

  const DemoModeBanner =
    demoMode && role != null ? (await import('./dev/DemoModeBanner')).DemoModeBanner : null;

  root.render(
    <StrictMode>
      <Flowbite>
        {DemoModeBanner != null && role != null && <DemoModeBanner role={role} />}
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
