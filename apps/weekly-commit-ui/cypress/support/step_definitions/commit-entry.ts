import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';

// ---------------------------------------------------------------------------
// commit-entry.feature -- Flow 1 step bindings (per-feature scoping per
// CLAUDE.md Common-Issues note about Cucumber step collisions).
//
// Implementations assume the WeekEditor integration from group 13b lands; the
// feature file is @pending-tagged so cypress run skips it until then.
// ---------------------------------------------------------------------------

const STUB_PLAN_BASE = {
  id: 'plan-new',
  employeeId: 'emp-test-1',
  weekStart: '2026-04-27',
  state: 'DRAFT',
  version: 0,
};

Given('the IC has no plan for the current week', () => {
  cy.intercept('GET', '/api/v1/plans/me/current', {
    statusCode: 404,
    body: { error: { code: 'NOT_FOUND', message: 'No plan for current week' } },
  }).as('getCurrentForMe');
  cy.intercept('POST', '/api/v1/plans', {
    statusCode: 201,
    body: { data: STUB_PLAN_BASE, meta: {} },
  }).as('createPlan');
});

Given('the IC visits the weekly-commit current-week route', () => {
  cy.visit('/#/weekly-commit/current');
});

Then('the blank state is visible', () => {
  cy.get('[data-testid="week-editor-blank"]').should('be.visible');
});

Then('the {string} button is visible', (label: string) => {
  cy.contains('button', label).should('be.visible');
});

When('I click the {string} button', (label: string) => {
  cy.contains('button', label).click();
});

Then('the DRAFT editor is visible', () => {
  cy.get('[data-testid="week-editor-draft"]').should('be.visible');
});

Then('the new plan has state {string}', (state: string) => {
  cy.get('[data-testid="state-badge"]').should('contain.text', state);
});

When('I open the RCDO picker', () => {
  // Stub the supporting-outcomes list for any scenario that drills into the picker.
  cy.intercept('GET', '/api/v1/rcdo/supporting-outcomes', {
    statusCode: 200,
    body: {
      data: [
        {
          id: 'so_alignment',
          label: 'Alignment tooling GA',
          active: true,
          breadcrumb: {
            rallyCry: { id: 'rc_01', label: 'Unblock product-led growth' },
            definingObjective: { id: 'do_04', label: 'Product-led GTM' },
            coreOutcome: { id: 'co_09', label: 'Tooling readiness' },
            supportingOutcome: { id: 'so_alignment', label: 'Alignment tooling GA' },
          },
        },
      ],
      meta: {},
    },
  }).as('getSupportingOutcomes');
  cy.get('[role="combobox"][aria-label="Supporting outcome"]').focus();
});

When('I select the supporting outcome {string}', (label: string) => {
  cy.contains('[role="option"]', label).click();
});

Then('the supporting outcome {string} is selected', (label: string) => {
  // Selection-state surface lands in 13b; until then this assertion documents the
  // intended UX (the picker should reflect what's chosen, e.g. via a "Selected:" pill).
  cy.contains('[data-testid="rcdo-selected-outcome"]', label).should('be.visible');
});

Then('the breadcrumb {string} is visible', (trail: string) => {
  cy.contains(trail).should('be.visible');
});
