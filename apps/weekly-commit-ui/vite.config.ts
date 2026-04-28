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

import { readFileSync } from 'node:fs';
import { defineConfig, type Plugin, type PluginOption } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';
import { visualizer } from 'rollup-plugin-visualizer';

/**
 * Exposes the cypress test private key (used by Cypress + Cucumber to sign JWTs)
 * as a virtual ES module the standalone-dev auth shim can import directly.
 * Sidesteps two things at once:
 *   - Vite's `?raw` import + fs.allow check -- the cypress/ path resolves outside
 *     the package serving allowlist under Yarn PnP, even with `allow: ['..']`.
 *   - The brittleness of round-tripping a multi-line PEM through `define` +
 *     JSON.stringify (esbuild's substitution can mangle escaped newlines).
 *
 * The virtual module name is opaque so it can never collide with a real package.
 * Tree-shaken from production federation builds because the only importer
 * (src/dev/devAuth.ts) is only reached under `import.meta.env.DEV`.
 */
function devPrivateKeyPlugin(): Plugin {
  const VIRTUAL_ID = 'virtual:wc-dev-private-key';
  const RESOLVED_ID = '\0' + VIRTUAL_ID;
  return {
    name: 'wc-dev-private-key',
    resolveId(id) {
      return id === VIRTUAL_ID ? RESOLVED_ID : null;
    },
    load(id) {
      if (id !== RESOLVED_ID) return null;
      const pem = readFileSync(
        new URL('./cypress/support/auth/keys/private-key.pem', import.meta.url),
        'utf8',
      );
      return `export default ${JSON.stringify(pem)};`;
    },
  };
}

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

// Demo deploys (VITE_DEMO_MODE=true) skip federation entirely. Federation builds
// produce a `remoteEntry.js` + `__federation_shared_*` chunks that expect a host
// to call `init()` on the share scope at runtime. In standalone production builds
// (Railway, S3+CloudFront, etc.) there's no host -- the federation runtime's
// `await importShared('react')` calls hang waiting for an init that never fires,
// boot stalls inside `main.tsx`'s top-level await, React never mounts, page goes
// white with no console error. `vite dev` papered over this with an automatic
// dev-mode share-scope init; production builds didn't.
//
// The federated remote is still the production target (consumed by the PA host
// per MVP18). Toggling federation off only for demo builds preserves the host
// integration path -- a regular `yarn build` from CI keeps emitting
// `remoteEntry.js` for the host team to pick up.
const DEMO_MODE = process.env.VITE_DEMO_MODE === 'true';

// Visualizer is opt-in: ANALYZE=1 yarn build emits dist/stats.html. Off by
// default so CI builds stay deterministic and don't ship the report into
// the federation upload. `as PluginOption` because rollup-plugin-visualizer
// types as a Rollup plugin and Vite's Plugin union is wider.
const ANALYZE = process.env.ANALYZE === '1' || process.env.ANALYZE === 'true';

export default defineConfig({
  plugins: [
    react(),
    !DEMO_MODE &&
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
    devPrivateKeyPlugin(),
    ANALYZE &&
      (visualizer({
        filename: 'dist/stats.html',
        gzipSize: true,
        brotliSize: true,
        // 'treemap' gives the most useful at-a-glance view of which
        // dependency dominates a chunk; matches what the PM remote ships.
        template: 'treemap',
        emitFile: false,
      }) as PluginOption),
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
    rollupOptions: {
      output: {
        // Manual chunking strategy: keep heavy non-shared deps out of the
        // route bundles so a hot edit to a route only re-downloads ~tens
        // of KB rather than the full vendor surface. React, ReactDOM,
        // react-router-dom, and @reduxjs/toolkit are NOT chunked here
        // -- they're declared `shared: { singleton: true }` above and
        // resolved against the host at runtime, so duplicating them into
        // a vendor chunk would undo the federation contract.
        //
        // Filename patterns are intentionally left at Vite/Rollup
        // defaults: the federation plugin owns `remoteEntry.js` via its
        // `filename` option, and Rollup's default chunk naming already
        // content-hashes everything else (CDN-cache-safe).
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('flowbite-react') || id.includes('/flowbite/')) {
            return 'vendor-flowbite';
          }
          if (id.includes('@sentry/')) return 'vendor-sentry';
          if (id.includes('jose')) return 'vendor-jose';
          return undefined;
        },
      },
    },
  },
  // Perf: pre-bundle the heavy deps that DraftMode (and its picker/form/tier
  // chain) pulls in. Without this, the first click of "Create plan" -- which
  // is also the first time DraftMode's deps are resolved -- pays an on-demand
  // `optimizeDeps` cost in dev mode that can stretch into multi-second
  // territory on cold starts. Listing them up front means Vite warm-bundles
  // them at server start, so the BlankState -> DraftMode transition is
  // bound by network + render only. Production builds ignore this block.
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'react-redux',
      'react-router-dom',
      '@reduxjs/toolkit',
      '@reduxjs/toolkit/query/react',
      // Specific subpaths only, mirroring the actual import sites in
      // src/main.tsx and src/components/ConflictToast.tsx. Listing the
      // bare 'flowbite-react' barrel here would re-introduce the
      // tree-shake hole the subpath imports just closed.
      'flowbite-react/components/Flowbite',
      'flowbite-react/components/Toast',
      'jose',
    ],
  },
  server: {
    // 4184 is reserved for this remote (ADR-0003). PM uses 4183.
    port: 4184,
    strictPort: true,
    cors: true,
    // Standalone-dev only. Forward /api/* and /actuator/* to the local Spring
    // service brought up by docker-compose.e2e.yml. Same-origin from the
    // browser's POV, so no CORS preflight / no need for a CORS bean on the
    // backend. Production topology has the PA host's reverse proxy doing the
    // same thing -- this mirrors it. Ignored entirely in `vite build` and the
    // federated-into-host runtime.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
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
    // Keep vitest from picking up Playwright specs (which live under tests/playwright/
    // and use the @playwright/test runner, not vitest).
    exclude: ['node_modules', 'dist', 'tests/playwright/**'],
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
