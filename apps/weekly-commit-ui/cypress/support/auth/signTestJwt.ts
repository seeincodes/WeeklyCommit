import { SignJWT, importPKCS8 } from 'jose';
import type { TestRole, TestUserClaims } from './testUsers';
import { TEST_USERS } from './testUsers';

/**
 * Signs an RS256 JWT for one of the four test roles using the test private key
 * checked in at `cypress/support/auth/keys/private-key.pem`. The matching
 * public key lives at the same path under both this UI workspace and
 * `apps/weekly-commit-service/src/main/resources/e2e-keys/`; the BE's
 * E2eJwtDecoderConfig (active under the `e2e` Spring profile) trusts that key.
 *
 * Why the private key is committed: it has zero authority outside the e2e
 * profile. See `cypress/support/auth/keys/README.md`.
 */

const ISSUER = 'wc-e2e-test-signer';
const TOKEN_TTL_SECONDS = 60 * 60; // 1 hour -- well past any single Cypress run

let cachedPrivateKey: CryptoKey | undefined;

async function loadPrivateKey(privateKeyPem: string): Promise<CryptoKey> {
  // jose caches nothing internally; cache here so we don't reparse PEM per token.
  if (cachedPrivateKey != null) return cachedPrivateKey;
  cachedPrivateKey = await importPKCS8(privateKeyPem, 'RS256');
  return cachedPrivateKey;
}

export async function signTestJwt(
  role: TestRole,
  privateKeyPem: string,
  overrides: Partial<TestUserClaims> = {},
): Promise<string> {
  const baseClaims = TEST_USERS[role];
  const claims: TestUserClaims = { ...baseClaims, ...overrides };

  const key = await loadPrivateKey(privateKeyPem);
  const now = Math.floor(Date.now() / 1000);

  const payload: Record<string, unknown> = {
    sub: claims.sub,
    org_id: claims.org_id,
    roles: claims.roles,
    timezone: claims.timezone,
  };
  // Spring's NimbusJwtDecoder + AuthenticatedPrincipal.optionalUuid() treats a
  // missing manager_id claim and a blank claim the same way (both -> Optional.empty).
  // Omit when null so the JWT shape matches the IC_NULL_MANAGER scenario exactly.
  if (claims.manager_id != null) {
    payload.manager_id = claims.manager_id;
  }

  return new SignJWT(payload)
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(ISSUER)
    .setIssuedAt(now)
    .setExpirationTime(now + TOKEN_TTL_SECONDS)
    .sign(key);
}

/** Resets the in-memory key cache. Used by tests that swap key material between cases. */
export function resetSignerCache(): void {
  cachedPrivateKey = undefined;
}
