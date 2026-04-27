/// <reference types="vite/client" />

// Build-time defines from vite.config.ts -- see the `define` block.
declare const __WC_GIT_SHA__: string;
declare const __WC_BUILD_TIME__: string;

// Virtual module from devPrivateKeyPlugin in vite.config.ts. Default export is
// the cypress test private key PEM. Read only by src/dev/devAuth.ts under
// import.meta.env.DEV; tree-shaken from prod federation builds.
declare module 'virtual:wc-dev-private-key' {
  const pem: string;
  export default pem;
}

interface ImportMetaEnv {
  readonly VITE_SENTRY_DSN?: string;
  // Standalone-dev only. Read by libs/rtk-api-client/src/api.ts (lazily, via a
  // narrow inline cast) and by src/dev/devAuth.ts. Federated mode inherits the
  // host's API URL and ignores this.
  readonly VITE_API_BASE_URL?: string;
  // Role the dev auth shim signs JWTs as. URL ?devRole=… wins at runtime.
  readonly VITE_DEV_AUTH_ROLE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
