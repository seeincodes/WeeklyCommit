# ADR-0003 — Mirror PM remote's `vite.config.ts` shape

- **Status:** Proposed (stubbed)
- **Date:** 2026-04-23
- **Deciders:** Xian (solo driver); PM remote owner TBD; PA host team TBD
- **Relates to:** Presearch §4 Module Federation, PRD [MVP18] [MVP19], MEMO decision (architectural — one deployment doesn't cover frontend federation), TASK_LIST group 9, assumption A3

## Context

The PA host app consumes the existing PM remote and we mirror that pattern
(A3). Host + remote must agree on: framework versions, shared-singleton
declarations, globalName for Module Federation, remoteEntry path convention,
dev-server port, build output shape.

Getting this shape wrong is a class of bug that only surfaces at runtime
inside the host: duplicate React, hook errors, shared-dep version
mismatches. The fix is always "match the host" — so the PM remote is our
reference because the host already loads it correctly.

**Inputs for this ADR are invented.** A reference vite config lives at
[../spikes/pm-remote-vite-config-reference.ts](../spikes/pm-remote-vite-config-reference.ts)
but the version pins, globalName, and exposes are all placeholder. Status
is **Proposed (stubbed)** until validated.

## Decision

`apps/weekly-commit-ui/vite.config.ts` (built in group 9) will mirror the
PM remote's shape with these values (to be validated):

**Module Federation plugin**: `@originjs/vite-plugin-federation`, same major
version as PM remote.

**Plugin config**
- `name: 'weekly_commit'` (the globalName host imports as `weekly_commit/WeeklyCommitModule`).
- `filename: 'remoteEntry.js'` (standard; matches PM).
- `exposes: { './WeeklyCommitModule': './src/WeeklyCommitModule.tsx' }`.
  Single expose in v1; additional exposes (if any) mirror PM's structure.
- `shared`: React 18, React DOM, React Router DOM, Redux Toolkit, RTK Query
  — all `singleton: true`, `eager: false`, with `requiredVersion` matching
  the host's pinned versions exactly.

**Build config**
- `target: 'esnext'` (matches PM; host is the consumer and dictates support).
- `modulePreload: false` — Module Federation loads chunks dynamically;
  preload lists fight the plugin.
- `minify: 'esbuild'` (matches PM).
- `cssCodeSplit: false` so remote CSS ships as a single bundle.

**Dev server**
- `strictPort: true` so port conflicts fail loudly.
- `cors: true` for local host-consumes-remote dev flow.
- Port: distinct from PM's (PM uses `4183` per stub; WC uses `4184` to
  coexist locally).

**Deploy shape**
- Bundle at `/remotes/weekly-commit/{version}/remoteEntry.js` on S3 +
  CloudFront (matches PM's versioned path convention from presearch §4).
- `max-age=31536000, immutable` on `/{version}/*`; `no-cache` on the
  manifest file the host reads to discover the current version.
- `{version}` = git SHA injected at build time via Vite `define`
  (`VITE_REMOTE_VERSION` env).

## Alternatives considered

1. **Webpack 5 Module Federation.** Rejected. Brief requires Vite 5. PM is
   Vite.
2. **Custom loader without `@originjs/vite-plugin-federation`.** Rejected.
   Reinvents Module Federation semantics and means we can't reuse host
   integration patterns.
3. **`eager: true` on shared singletons.** Rejected. Explodes bundle size,
   forces every consumer to load React up front, breaks the "shared
   singleton, late-binding" contract. Brief requires `eager: false`.
4. **Different globalName convention.** Rejected. Host's import path
   (`performance_management/PerformanceManagementModule`) is our reference;
   `weekly_commit/WeeklyCommitModule` keeps convention parity.

## Consequences

- **Positive**: group 9 can scaffold `vite.config.ts` from a concrete
  template; risk of runtime integration bugs is contained to version pins,
  which are a narrow validation surface.
- **Negative**: every assumption in this ADR (React 18.3.x, Router 6.26.x,
  RTK 2.2.x, plugin major version) could be wrong. Bundle deploys but
  breaks at `host → remote.loadShareScope()` time. The remote-in-isolation
  Playwright smoke (group 9) won't catch version drift — only a real host
  mount does (group 13 Cypress). This is the biggest integration risk of
  the project and is fully explained by "we haven't seen PM's config."
- **Follow-ups**:
  - Real PM config copy-pasted into the stub file before group 9 starts.
  - Host's `package.json` + any federation manifest copied too (for
    shared version pins).
  - A "semver-bump of any shared dep requires coordinated host+remote
    release" policy in the runbook (referenced in [CLAUDE.md](../../CLAUDE.md)'s
    tech stack lock).
  - A weekly CI smoke test that runs `weekly-commit-ui` against the host's
    `main` branch; any duplicate-React warning fails it.

## Validation

Upgrades to **Accepted** when all four are true:

1. A real copy of the PM remote's `vite.config.ts` lives at
   [../spikes/pm-remote-vite-config-reference.ts](../spikes/pm-remote-vite-config-reference.ts)
   (or a `.pm-actual.ts` next to it), replacing the invented version.
2. Host's shared-dep versions are copied from the host's `package.json`
   into a table in this ADR (or into a new ADR that supersedes this).
3. `weekly-commit-ui` bundle loads inside the PA host without
   "Invalid hook call" or "duplicate React" warnings, in a staging env.
4. A Cypress scenario in group 13 mounts the remote from the host's
   `main` and asserts the `weekly_commit/WeeklyCommitModule` container
   renders.
