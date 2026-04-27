/**
 * Canonical test-user fixtures for the Cypress + Cucumber suite. Each role maps
 * to a stable set of JWT claims so scenarios across .feature files use the
 * same identities. Drift here is intentional: change a uuid only if a scenario
 * explicitly needs a different one (passing a `claims` override to `signTestJwt`).
 *
 * Per [MVP20]: required claims are sub, org_id, manager_id (nullable), roles, timezone.
 * Per TASK_LIST group 13 subtask 4: four roles -- IC (with manager), IC (null manager),
 * MANAGER, ADMIN.
 */

export type TestRole = 'IC' | 'IC_NULL_MANAGER' | 'MANAGER' | 'ADMIN';

export interface TestUserClaims {
  sub: string;
  org_id: string;
  manager_id: string | null;
  roles: string[];
  timezone: string;
}

const ORG_ID = '11111111-1111-1111-1111-111111111111';
const MANAGER_EMPLOYEE_ID = '22222222-2222-2222-2222-222222222222';
const ADMIN_EMPLOYEE_ID = '33333333-3333-3333-3333-333333333333';
const IC_EMPLOYEE_ID = '44444444-4444-4444-4444-444444444444';
const IC_ORPHAN_EMPLOYEE_ID = '55555555-5555-5555-5555-555555555555';

export const TEST_USERS: Record<TestRole, TestUserClaims> = {
  IC: {
    sub: IC_EMPLOYEE_ID,
    org_id: ORG_ID,
    manager_id: MANAGER_EMPLOYEE_ID,
    roles: ['IC'],
    timezone: 'America/New_York',
  },
  IC_NULL_MANAGER: {
    sub: IC_ORPHAN_EMPLOYEE_ID,
    org_id: ORG_ID,
    manager_id: null,
    roles: ['IC'],
    timezone: 'America/New_York',
  },
  MANAGER: {
    sub: MANAGER_EMPLOYEE_ID,
    org_id: ORG_ID,
    manager_id: null, // skip-level + above are out of scope for v1
    roles: ['IC', 'MANAGER'], // managers are also ICs
    timezone: 'America/New_York',
  },
  ADMIN: {
    sub: ADMIN_EMPLOYEE_ID,
    org_id: ORG_ID,
    manager_id: null,
    roles: ['ADMIN'],
    timezone: 'UTC',
  },
};
