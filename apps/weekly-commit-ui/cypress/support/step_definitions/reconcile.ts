import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';

// ---------------------------------------------------------------------------
// reconcile.feature -- Flow 3 step bindings.
//
// Each scenario assumes the LOCKED-plan + post-day-4 reconcile-eligibility
// path; the WeekEditor's mode resolver picks ReconcileMode in that case.
// ---------------------------------------------------------------------------

const LOCKED_RECONCILE_PLAN = {
  id: 'plan-1',
  employeeId: 'emp-test-1',
  weekStart: '2026-04-20', // 7 days ago from a stubbed clock; well past day 4
  state: 'LOCKED',
  version: 1,
  lockedAt: '2026-04-20T17:00:00Z',
};

const COMMITS = [
  {
    id: 'a',
    planId: 'plan-1',
    title: 'Commit A',
    supportingOutcomeId: 'so_alignment',
    chessTier: 'ROCK',
    displayOrder: 0,
    actualStatus: 'PENDING',
  },
  {
    id: 'b',
    planId: 'plan-1',
    title: 'Commit B',
    supportingOutcomeId: 'so_alignment',
    chessTier: 'PEBBLE',
    displayOrder: 1,
    actualStatus: 'PENDING',
  },
  {
    id: 'c',
    planId: 'plan-1',
    title: 'Commit C',
    supportingOutcomeId: 'so_alignment',
    chessTier: 'SAND',
    displayOrder: 2,
    actualStatus: 'PENDING',
  },
];

Given('the IC has a LOCKED plan with weekStart >= 4 days ago and commits a, b, c', () => {
  cy.intercept('GET', '/api/v1/plans/me/current', {
    statusCode: 200,
    body: { data: LOCKED_RECONCILE_PLAN, meta: {} },
  }).as('getCurrentForMe');
  cy.intercept('GET', `/api/v1/plans/${LOCKED_RECONCILE_PLAN.id}/commits`, {
    statusCode: 200,
    body: { data: COMMITS, meta: {} },
  }).as('listCommits');
  cy.intercept('PATCH', '/api/v1/commits/*').as('patchCommit');
  cy.intercept('PATCH', `/api/v1/plans/${LOCKED_RECONCILE_PLAN.id}`).as('patchPlan');
  cy.intercept('POST', '/api/v1/commits/*/carry-forward').as('carryForward');
  cy.intercept('POST', `/api/v1/plans/${LOCKED_RECONCILE_PLAN.id}/transitions`, {
    statusCode: 200,
    body: {
      data: { ...LOCKED_RECONCILE_PLAN, state: 'RECONCILED', reconciledAt: '2026-04-25T18:00:00Z' },
      meta: {},
    },
  }).as('transition');
});

Given('the IC visits the weekly-commit current-week route', () => {
  cy.visit('/#/weekly-commit/current');
});

Then('the reconciliation table is visible', () => {
  cy.get('[data-testid="week-editor-reconcile"]').should('be.visible');
});

Then('each commit row has DONE / PARTIAL / MISSED radios', () => {
  cy.get('[role="radiogroup"]').each(($group) => {
    cy.wrap($group).find('input[type="radio"][value="DONE"]').should('exist');
    cy.wrap($group).find('input[type="radio"][value="PARTIAL"]').should('exist');
    cy.wrap($group).find('input[type="radio"][value="MISSED"]').should('exist');
  });
});

When('I select the {string} status for commit {string}', (status: string, commitId: string) => {
  cy.get(`[data-testid="reconcile-row-${commitId}"]`)
    .find(`input[type="radio"][value="${status}"]`)
    .check();
});

Then('the API receives PATCH /commits/{string} with actualStatus DONE', (commitId: string) => {
  cy.wait('@patchCommit')
    .its('request')
    .should((req) => {
      expect(req.url).to.match(new RegExp(`/api/v1/commits/${commitId}$`));
      expect(req.body).to.deep.equal({ actualStatus: 'DONE' });
    });
});

When('I type a {int}-character reflection note', (count: number) => {
  cy.get('[role="textbox"][aria-label="Reflection"]').type('a'.repeat(count));
});

Then('the reflection counter reads {string}', (label: string) => {
  cy.get('[data-testid="reflection-counter"]').should('contain.text', label);
});

Then('the API receives PATCH /plans with the reflection body', () => {
  cy.wait('@patchPlan')
    .its('request.body')
    .should((body) => {
      expect(body).to.have.property('reflectionNote');
    });
});

Then('the reflection counter has the warning style', () => {
  cy.get('[data-testid="reflection-counter"]').should('have.attr', 'data-warning', 'true');
});

Given('commit {string} was reconciled MISSED', (commitId: string) => {
  // Re-stub the commits list with one of them already MISSED so the carry button is eligible.
  cy.intercept('GET', `/api/v1/plans/${LOCKED_RECONCILE_PLAN.id}/commits`, {
    statusCode: 200,
    body: {
      data: COMMITS.map((c) => (c.id === commitId ? { ...c, actualStatus: 'MISSED' } : c)),
      meta: {},
    },
  });
});

When('I click the {string} button on commit {string}', (label: string, commitId: string) => {
  cy.get(`[data-testid="reconcile-row-${commitId}"]`).contains('button', label).click();
});

Then('the API receives POST /commits/{string}/carry-forward', (commitId: string) => {
  cy.wait('@carryForward')
    .its('request.url')
    .should('match', new RegExp(`/api/v1/commits/${commitId}/carry-forward$`));
});

When('I click the {string} button', (label: string) => {
  cy.contains('button', label).click();
});

Then('the API receives POST /plans/transitions with target RECONCILED', () => {
  cy.wait('@transition').its('request.body').should('deep.equal', { to: 'RECONCILED' });
});

Then('the plan state badge reads {string}', (state: string) => {
  cy.get('[data-testid="state-badge"]').should('contain.text', state);
});
