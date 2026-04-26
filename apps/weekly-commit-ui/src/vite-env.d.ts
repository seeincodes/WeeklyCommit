/// <reference types="vite/client" />

// Build-time defines from vite.config.ts -- see the `define` block.
declare const __WC_GIT_SHA__: string;
declare const __WC_BUILD_TIME__: string;

interface ImportMetaEnv {
  readonly VITE_SENTRY_DSN?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
