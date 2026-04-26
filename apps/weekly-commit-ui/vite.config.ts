/// <reference types="vitest" />
// Vite + Module Federation config for the Weekly Commit remote.
//
// Shape mirrors the PA host's PM remote per ADR-0003
// (../../docs/adr/0003-pm-remote-vite-config-mirror.md). The reference at
// ../../docs/spikes/pm-remote-vite-config-reference.ts is a STUB; this file
// uses the values codified in ADR-0003 (name `weekly_commit`, port 4184,
// expose `./WeeklyCommitModule`, shared singletons React 18 / Router 6 /
// RTK 2). Where the spike and ADR-0003 disagree, ADR-0003 wins.
//
// Shared deps MUST stay version-aligned with the host. Bumping any major
// (or minor that includes a runtime contract change) without coordinating
// with the host will surface as duplicate-React / "Invalid hook call" at
// runtime when the host loads our remoteEntry.js. See CLAUDE.md tech-stack
// lock for the policy.

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

// Singleton requirements track package.json's pinned versions. Keep the
// caret prefix (^X.Y.Z) so a host shipping a newer compatible patch/minor
// still satisfies us. eager: false per MEMO -- the host owns the loading
// schedule, not us.
const SHARED = {
  react: { singleton: true, eager: false, requiredVersion: '^18.3.1' },
  'react-dom': { singleton: true, eager: false, requiredVersion: '^18.3.1' },
  'react-router-dom': { singleton: true, eager: false, requiredVersion: '^6.26.2' },
  '@reduxjs/toolkit': { singleton: true, eager: false, requiredVersion: '^2.2.7' },
  '@reduxjs/toolkit/query': { singleton: true, eager: false, requiredVersion: '^2.2.7' },
} as const;

export default defineConfig({
  plugins: [
    react(),
    federation({
      // Globalname the host imports as `weekly_commit/WeeklyCommitModule`.
      // snake_case is intentional and mirrors the host's PM convention.
      name: 'weekly_commit',
      filename: 'remoteEntry.js',
      exposes: {
        './WeeklyCommitModule': './src/WeeklyCommitModule.tsx',
      },
      shared: SHARED,
    }),
  ],
  build: {
    // Host is the consumer and dictates browser support; matches PM remote.
    target: 'esnext',
    // Module Federation loads shared chunks dynamically; preload lists
    // fight the plugin (ADR-0003).
    modulePreload: false,
    minify: 'esbuild',
    // Single CSS bundle so the host can load remote styles in one fetch.
    cssCodeSplit: false,
  },
  server: {
    // 4184 is reserved for this remote (ADR-0003). PM uses 4183.
    port: 4184,
    strictPort: true,
    cors: true,
  },
  define: {
    // Build-time version stamp (subtask 8). CI sets GIT_SHA; locally we
    // fall back to 'dev'. Surfaces as `__WC_GIT_SHA__` in app code -- see
    // src/vite-env.d.ts for the type declaration. Sentry release is also
    // pinned to this value so RUM errors map back to the deployed bundle.
    //
    // Vitest reads this same `define` block at test time, so the placeholder's
    // `{__WC_GIT_SHA__}` expression resolves to `'dev'` in unit tests without
    // any extra wiring -- which is the whole reason we extend vite.config.ts
    // rather than fork a separate vitest.config.ts.
    __WC_GIT_SHA__: JSON.stringify(process.env.GIT_SHA ?? 'dev'),
    __WC_BUILD_TIME__: JSON.stringify(new Date().toISOString()),
  },
  test: {
    // Vitest config (subtask 9.5). Lives here rather than in a parallel
    // vitest.config.ts so the `define` block above and any future asset
    // resolution stay in sync between dev/build and test runs.
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    coverage: {
      // v8 provider is the tech-stack lock (CLAUDE.md). Istanbul is not
      // an option for this project.
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        // Standalone bootstrap; covered by Playwright remote-in-isolation
        // smoke (subtask 9.6), not unit tests.
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/**/*.d.ts',
        // Tests shouldn't count toward their own coverage. Vitest's
        // default exclude list covers this, but providing our own
        // `exclude` replaces the defaults rather than merging, so we
        // re-state the test glob explicitly.
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/setupTests.ts',
      ],
      // Targets per docs/TESTING_STRATEGY.md (>= 80% line coverage gate).
      // Statements/branches/functions held to the same bar so the gate
      // doesn't silently drift if line coverage stays high but branch
      // coverage rots.
      thresholds: {
        lines: 80,
        statements: 80,
        branches: 80,
        functions: 80,
      },
    },
  },
});
