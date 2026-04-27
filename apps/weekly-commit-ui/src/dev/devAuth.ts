// Standalone-dev-only Authorization injector.
//
// Why this exists: in production, the PA host owns auth and injects an Auth0
// JWT into outgoing fetches before they leave the iframe-equivalent boundary.
// In standalone `vite dev` there is no host -- fetches go to the backend with
// no Authorization header, the e2e-profile Spring service rejects with 401,
// and every screen renders an error state. This file mints a self-signed JWT
// using the same private key the Cypress suite signs with (E2eJwtDecoderConfig
// trusts its public half) and patches global fetch to attach it.
//
// Strict scope:
//   - Imported only from src/main.tsx (the standalone entry).
//   - Wrapped in `if (import.meta.env.DEV)` at the call site so prod builds
//     tree-shake the entire module away. The PEM string + jose dep then drop
//     out of the bundle.
//   - Does NOT touch the federated WeeklyCommitModule export path.
//
// Role override: ?devRole=IC|IC_NULL_MANAGER|MANAGER|ADMIN in the URL beats
// VITE_DEV_AUTH_ROLE which beats the hardcoded MANAGER default.

import { SignJWT, importPKCS8 } from 'jose';
// Virtual module supplied by `devPrivateKeyPlugin` in vite.config.ts -- reads
// the cypress test key at Vite-config time and exposes it as a default export.
// Sidesteps both the `?raw` + fs.allow path (Yarn PnP confused the workspace-
// root check) and `define` + JSON.stringify newline-escaping fragility.
import privateKeyPem from 'virtual:wc-dev-private-key';

type DevRole = 'IC' | 'IC_NULL_MANAGER' | 'MANAGER' | 'ADMIN';

interface DevClaims {
  sub: string;
  org_id: string;
  manager_id: string | null;
  roles: string[];
  timezone: string;
}

// Mirrors cypress/support/auth/testUsers.ts. Kept inline here rather than
// imported because the Cypress source isn't part of the app's TS project and
// pulling it in would drag the @badeball/cypress-cucumber-preprocessor types
// into the dev build for no reason.
const ORG_ID = '11111111-1111-1111-1111-111111111111';
const TEST_USERS: Record<DevRole, DevClaims> = {
  IC: {
    sub: '44444444-4444-4444-4444-444444444444',
    org_id: ORG_ID,
    manager_id: '22222222-2222-2222-2222-222222222222',
    roles: ['IC'],
    timezone: 'America/New_York',
  },
  IC_NULL_MANAGER: {
    sub: '55555555-5555-5555-5555-555555555555',
    org_id: ORG_ID,
    manager_id: null,
    roles: ['IC'],
    timezone: 'America/New_York',
  },
  MANAGER: {
    sub: '22222222-2222-2222-2222-222222222222',
    org_id: ORG_ID,
    manager_id: null,
    roles: ['IC', 'MANAGER'],
    timezone: 'America/New_York',
  },
  ADMIN: {
    sub: '33333333-3333-3333-3333-333333333333',
    org_id: ORG_ID,
    manager_id: null,
    roles: ['ADMIN'],
    timezone: 'UTC',
  },
};

function resolveRole(): DevRole {
  const fromUrl = new URLSearchParams(window.location.search).get('devRole');
  const fromEnv = import.meta.env.VITE_DEV_AUTH_ROLE;
  const candidate = fromUrl ?? fromEnv ?? 'MANAGER';
  return (candidate in TEST_USERS ? candidate : 'MANAGER') as DevRole;
}

async function mintJwt(role: DevRole): Promise<string> {
  const claims = TEST_USERS[role];
  const key = await importPKCS8(privateKeyPem, 'RS256');
  const now = Math.floor(Date.now() / 1000);

  const payload: Record<string, unknown> = {
    sub: claims.sub,
    org_id: claims.org_id,
    roles: claims.roles,
    timezone: claims.timezone,
  };
  if (claims.manager_id != null) {
    payload.manager_id = claims.manager_id;
  }

  return new SignJWT(payload)
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer('wc-e2e-test-signer')
    .setIssuedAt(now)
    .setExpirationTime(now + 60 * 60)
    .sign(key);
}

let installed = false;

/**
 * Patches global fetch to attach `Authorization: Bearer <signed JWT>` to
 * every request whose URL targets the configured backend. Idempotent --
 * second call is a no-op so HMR reloads don't stack interceptors.
 *
 * The token is minted once per page load and reused; expiry is 1h, much
 * longer than any dev session is likely to keep one tab open without a
 * Vite hot-reload triggering a full re-init.
 */
export async function installDevAuth(): Promise<DevRole> {
  if (installed) return resolveRole();
  installed = true;

  const role = resolveRole();
  const token = await mintJwt(role);

  const originalFetch = window.fetch.bind(window);
  window.fetch = (input, init) => {
    const url =
      typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    // Match by path so we attach the token whether RTK Query targets the Vite
    // proxy (same-origin /api/...) or a fully-qualified backend URL. Anything
    // that isn't an API or actuator request (Vite HMR pings, source maps, asset
    // fetches) passes through untouched.
    const path = url.startsWith('http') ? new URL(url).pathname : url;
    const isBackendCall = path.startsWith('/api/') || path.startsWith('/actuator/');
    if (!isBackendCall) {
      return originalFetch(input, init);
    }
    const headers = new Headers(
      init?.headers ?? (input instanceof Request ? input.headers : undefined),
    );
    if (!headers.has('authorization')) {
      headers.set('authorization', `Bearer ${token}`);
    }
    return originalFetch(input, { ...init, headers });
  };

  // Surface the active role in the title so it's visually obvious which
  // identity the dev session is acting as. No floating UI bar to maintain.
  if (typeof document !== 'undefined') {
    document.title = `Weekly Commit [dev:${role}]`;
  }

  return role;
}
