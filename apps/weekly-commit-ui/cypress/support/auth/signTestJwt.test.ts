// @vitest-environment node
import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { jwtVerify, importSPKI } from 'jose';
import { signTestJwt, resetSignerCache } from './signTestJwt';
import { TEST_USERS } from './testUsers';

const PRIVATE_KEY_PEM = readFileSync(resolve(__dirname, 'keys/private-key.pem'), 'utf8');
const PUBLIC_KEY_PEM = readFileSync(resolve(__dirname, 'keys/public-key.pem'), 'utf8');

describe('signTestJwt', () => {
  beforeEach(() => {
    resetSignerCache();
  });

  it('produces a JWT verifiable with the matching public key', async () => {
    const token = await signTestJwt('IC', PRIVATE_KEY_PEM);
    const publicKey = await importSPKI(PUBLIC_KEY_PEM, 'RS256');
    const { payload, protectedHeader } = await jwtVerify(token, publicKey, {
      issuer: 'wc-e2e-test-signer',
    });
    expect(protectedHeader.alg).toBe('RS256');
    expect(payload.sub).toBe(TEST_USERS.IC.sub);
    expect(payload.org_id).toBe(TEST_USERS.IC.org_id);
    expect(payload.roles).toEqual(['IC']);
  });

  it('emits manager_id for IC (with manager) and omits it for IC_NULL_MANAGER', async () => {
    const publicKey = await importSPKI(PUBLIC_KEY_PEM, 'RS256');

    const tokenWithManager = await signTestJwt('IC', PRIVATE_KEY_PEM);
    const { payload: payloadWithManager } = await jwtVerify(tokenWithManager, publicKey);
    expect(payloadWithManager.manager_id).toBe(TEST_USERS.IC.manager_id);

    const tokenNullManager = await signTestJwt('IC_NULL_MANAGER', PRIVATE_KEY_PEM);
    const { payload: payloadNullManager } = await jwtVerify(tokenNullManager, publicKey);
    // BE's optionalUuid() treats missing == blank == Optional.empty; we omit rather than set null.
    expect('manager_id' in payloadNullManager).toBe(false);
  });

  it('signs MANAGER with both IC and MANAGER roles (managers are also ICs)', async () => {
    const publicKey = await importSPKI(PUBLIC_KEY_PEM, 'RS256');
    const token = await signTestJwt('MANAGER', PRIVATE_KEY_PEM);
    const { payload } = await jwtVerify(token, publicKey);
    expect(payload.roles).toEqual(['IC', 'MANAGER']);
  });

  it('signs ADMIN with the ADMIN role only', async () => {
    const publicKey = await importSPKI(PUBLIC_KEY_PEM, 'RS256');
    const token = await signTestJwt('ADMIN', PRIVATE_KEY_PEM);
    const { payload } = await jwtVerify(token, publicKey);
    expect(payload.roles).toEqual(['ADMIN']);
  });

  it('honors per-call overrides without mutating the canonical fixture', async () => {
    const customSub = '99999999-9999-9999-9999-999999999999';
    const token = await signTestJwt('IC', PRIVATE_KEY_PEM, { sub: customSub });
    const publicKey = await importSPKI(PUBLIC_KEY_PEM, 'RS256');
    const { payload } = await jwtVerify(token, publicKey);
    expect(payload.sub).toBe(customSub);
    // Canonical fixture untouched for the next test.
    expect(TEST_USERS.IC.sub).not.toBe(customSub);
  });

  it('sets exp to roughly 1 hour from now', async () => {
    const before = Math.floor(Date.now() / 1000);
    const token = await signTestJwt('IC', PRIVATE_KEY_PEM);
    const after = Math.floor(Date.now() / 1000);
    const publicKey = await importSPKI(PUBLIC_KEY_PEM, 'RS256');
    const { payload } = await jwtVerify(token, publicKey);
    expect(payload.exp).toBeGreaterThanOrEqual(before + 3600);
    expect(payload.exp).toBeLessThanOrEqual(after + 3600 + 1);
  });
});
