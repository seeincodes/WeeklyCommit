import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';

// ---------------------------------------------------------------------------
// lock-week.feature -- Flow 2 step bindings.
// ---------------------------------------------------------------------------

const DRAFT_PLAN = {
  id: 'plan-1',
  employeeId: 'emp-test-1',
  weekStart: '2026-04-27',
  state: 'DRAFT',
  version: 0,
};

const LOCKED_PLAN = {
  ...DRAFT_PLAN,
  state: 'LOCKED',
  version: 1,
  lockedAt: '2026-04-27T17:00:00Z',
};

Given('the IC has a DRAFT plan with at least one commit', () => {
  cy.intercept('GET', '/api/v1/plans/me/current', {
    statusCode: 200,
    body: { data: DRAFT_PLAN, meta: {} },
  }).as('getCurrentForMe');
  cy.intercept('GET', `/api/v1/plans/${DRAFT_PLAN.id}/commits`, {
    statusCode: 200,
    body: {
      data: [
        {
          id: 'c-1',
          planId: DRAFT_PLAN.id,
          title: 'Land RCDO picker spike',
          supportingOutcomeId: 'so_alignment',
          chessTier: 'ROCK',
          displayOrder: 0,
          actualStatus: 'PENDING',
        },
      ],
      meta: {},
    },
  }).as('listCommits');
  cy.intercept('POST', `/api/v1/plans/${DRAFT_PLAN.id}/transitions`, {
    statusCode: 200,
    body: { data: LOCKED_PLAN, meta: {} },
  }).as('transition');
});

Given('the IC visits the weekly-commit current-week route', () => {
  cy.visit('/#/weekly-commit/current');
});

When('I click the {string} button', (label: string) => {
  cy.contains('button', label).click();
});

Then('the plan state badge reads {string}', (state: string) => {
  cy.get('[data-testid="state-badge"]').should('contain.text', state);
});

Then('the editor is in read-only mode pre-day-4', () => {
  cy.get('[data-testid="week-editor-locked-readonly"]').should('be.visible');
});

Then('the next-action hint reads {string}', (hint: string) => {
  cy.get('[data-testid="state-badge-hint"]').should('contain.text', hint);
});

Given('a stale version is held client-side', () => {
  // Override the transition stub to return 409 once, then 200 (refetch path).
  cy.intercept('POST', `/api/v1/plans/${DRAFT_PLAN.id}/transitions`, {
    statusCode: 409,
    body: {
      error: {
        code: 'CONFLICT_OPTIMISTIC_LOCK',
        message: 'Refetch and retry',
      },
    },
  }).as('transition409');
});

Then('the conflict toast is visible', () => {
  cy.get('[data-testid="conflict-toast"]').should('be.visible');
});

Then('the plan refetches automatically', () => {
  cy.wait('@getCurrentForMe');
});
