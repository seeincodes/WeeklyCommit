# @wc/weekly-commit-ui

Vite 5 + React 18 federated remote consumed by the PA host. Exposes
`./WeeklyCommitModule` via `@originjs/vite-plugin-federation` with shared
singletons for React, React DOM, React Router, and Redux Toolkit (all
`eager: false`).

## Local development

- `yarn workspace @wc/weekly-commit-ui dev` — standalone dev mode on
  `http://localhost:4184` (uses `index.html` + `src/main.tsx` with a
  `HashRouter`; this is the Playwright remote-in-isolation path).
- `yarn workspace @wc/weekly-commit-ui build` — emits `dist/` containing
  `remoteEntry.js` plus chunked shared deps. CI sets `GIT_SHA=<sha>` so
  the bundle stamps `__WC_GIT_SHA__` for Sentry release tracking.
- `yarn workspace @wc/weekly-commit-ui clean` — wipes `dist/` and the
  Vite cache (`node_modules/.vite`).

## Production federation

The PA host registers this remote as `weekly_commit/WeeklyCommitModule`
and mounts it inside its own `<BrowserRouter>` + Sentry context. The
host owns auth, theming, and routing; we contribute the federated bundle
and re-use the host's React + Redux instances via the singleton config.

## Smoke tests (Playwright)

```bash
# One-time browser install (after yarn install)
yarn workspace @wc/weekly-commit-ui exec playwright install chromium

# Run the standalone-isolation smoke
yarn workspace @wc/weekly-commit-ui test:playwright
```

The smoke boots the Vite dev server on :4184 and asserts the federated module mounts. Federated-inside-host coverage is the Cypress + Cucumber suite (separate scope).

## References

- [docs/adr/0003-pm-remote-vite-config-mirror.md](../../docs/adr/0003-pm-remote-vite-config-mirror.md) — Vite + Module Federation config rationale (mirror of PM remote).
- [docs/adr/0004-flowbite-token-override.md](../../docs/adr/0004-flowbite-token-override.md) — UI library + theming approach (lands in a sibling subtask).
- [docs/PRD.md](../../docs/PRD.md) `[MVP18]` / `[MVP19]` — scope.
- [docs/TECH_STACK.md](../../docs/TECH_STACK.md#environment-variables) — env var contract.
