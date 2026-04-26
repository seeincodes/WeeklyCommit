// STUB — invented reference based on how we expect the PM remote is configured
// per presearch §4 / §6 + @originjs/vite-plugin-federation docs.
//
// NOT a live copy. Must be validated against the actual PM remote before
// weekly-commit-ui mirrors the shape in group 9. See docs/adr/0003-pm-remote-vite-config-mirror.md.

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import federation from '@originjs/vite-plugin-federation';

// Shared singletons — MUST match host versions exactly, eager: false per MEMO.
// Actual values to confirm against the host's package.json + federation manifest.
const SHARED = {
  react: { singleton: true, eager: false, requiredVersion: '^18.3.0' },
  'react-dom': { singleton: true, eager: false, requiredVersion: '^18.3.0' },
  'react-router-dom': { singleton: true, eager: false, requiredVersion: '^6.26.0' },
  '@reduxjs/toolkit': { singleton: true, eager: false, requiredVersion: '^2.2.0' },
  '@reduxjs/toolkit/query': { singleton: true, eager: false, requiredVersion: '^2.2.0' },
} as const;

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'performance_management', // PM remote's globalName
      filename: 'remoteEntry.js',
      exposes: {
        './PerformanceManagementModule': './src/PerformanceManagementModule.tsx',
        // Assumed additional exposes; confirm with PM remote owner.
        './PerformanceManagementRoutes': './src/routes.tsx',
      },
      shared: SHARED,
    }),
  ],
  build: {
    target: 'esnext',
    modulePreload: false,
    minify: 'esbuild',
    cssCodeSplit: false,
    // PM remote published at /remotes/performance-management/{version}/remoteEntry.js
    // Served from S3 + CloudFront, long-cache on versioned paths, no-cache on manifest.
  },
  server: {
    port: 4183, // invented; PM remote's dev port
    strictPort: true,
    cors: true,
  },
});
