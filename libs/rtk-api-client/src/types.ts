import type { components, operations } from '@wc/contracts';

// Narrow aliases over the optional-everywhere openapi-typescript shapes.
// We mark fields as required when the server contract guarantees them so
// downstream UI code doesn't have to assert non-null on every render.
export type WeeklyPlanResponse = NonNullable<components['schemas']['WeeklyPlanResponse']> & {
  id: string;
  employeeId: string;
  weekStart: string;
  state: 'DRAFT' | 'LOCKED' | 'RECONCILED' | 'ARCHIVED';
  version: number;
};

export type WeeklyCommitResponse = NonNullable<components['schemas']['WeeklyCommitResponse']> & {
  id: string;
  planId: string;
  title: string;
  supportingOutcomeId: string;
  chessTier: 'ROCK' | 'PEBBLE' | 'SAND';
  displayOrder: number;
  actualStatus: 'PENDING' | 'DONE' | 'PARTIAL' | 'MISSED';
};

export type ManagerReviewResponse = NonNullable<components['schemas']['ManagerReviewResponse']> & {
  id: string;
  planId: string;
  managerId: string;
};

export type RollupResponse = NonNullable<components['schemas']['RollupResponse']>;
export type MemberCard = NonNullable<components['schemas']['MemberCard']>;
export type AuditLogResponse = NonNullable<components['schemas']['AuditLogResponse']>;
export type UnassignedEmployeeResponse = NonNullable<
  components['schemas']['UnassignedEmployeeResponse']
>;

export type CreateCommitRequest = components['schemas']['CreateCommitRequest'];
export type UpdateCommitRequest = components['schemas']['UpdateCommitRequest'];
export type CreateReviewRequest = components['schemas']['CreateReviewRequest'];
export type TransitionRequest = components['schemas']['TransitionRequest'];
export type UpdateReflectionRequest = components['schemas']['UpdateReflectionRequest'];

export type Operation<K extends keyof operations> = operations[K];
