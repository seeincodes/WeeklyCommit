import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';

// ---------------------------------------------------------------------------
// manager-review.feature -- Flow 4 step bindings.
// ---------------------------------------------------------------------------

const ROLLUP_RESPONSE = {
  alignmentPct: 0.91,
  completionPct: 0.76,
  tierDistribution: { ROCK: 14, PEBBLE: 39, SAND: 22 },
  unreviewedCount: 3,
  stuckCommitCount: 2,
  members: [
    {
      employeeId: 'emp-flagged',
      name: 'Ben Flagged',
      planState: 'RECONCILED',
      topRock: { commitId: 'c-1', title: 'Land RCDO picker' },
      tierCounts: { ROCK: 2, PEBBLE: 3, SAND: 1 },
      reflectionPreview: 'Unblocked the picker; WireMock contract drift took longer than expected.',
      flags: ['UNREVIEWED_72H'],
    },
    {
      employeeId: 'emp-clean-1',
      name: 'Ada Clean',
      planState: 'RECONCILED',
      topRock: { commitId: 'c-2', title: 'Doc draft' },
      tierCounts: { ROCK: 1, PEBBLE: 4, SAND: 0 },
      reflectionPreview: 'Smooth week.',
      flags: [],
    },
    {
      employeeId: 'emp-clean-2',
      name: 'Cleo Clean',
      planState: 'RECONCILED',
      topRock: { commitId: 'c-3', title: 'Schema migration' },
      tierCounts: { ROCK: 1, PEBBLE: 2, SAND: 1 },
      reflectionPreview: 'Migration landed.',
      flags: [],
    },
  ],
};

const FLAGGED_PLAN = {
  id: 'plan-flagged',
  employeeId: 'emp-flagged',
  weekStart: '2026-04-20',
  state: 'RECONCILED',
  version: 4,
  reflectionNote: 'Full reflection note shown only inside the IC drawer.',
  reconciledAt: '2026-04-25T18:00:00Z',
};

Given('the manager has 3 direct reports with one flagged member', () => {
  cy.intercept('GET', '/api/v1/rollup/team*', {
    statusCode: 200,
    body: { data: ROLLUP_RESPONSE, meta: {} },
  }).as('getTeamRollup');
  cy.intercept('GET', '/api/v1/plans*', {
    statusCode: 200,
    body: { data: FLAGGED_PLAN, meta: {} },
  }).as('getPlanByEmployeeAndWeek');
  cy.intercept('GET', `/api/v1/plans/${FLAGGED_PLAN.id}/commits`, {
    statusCode: 200,
    body: {
      data: [
        {
          id: 'c-r',
          planId: FLAGGED_PLAN.id,
          title: 'A rock',
          supportingOutcomeId: 'so_alignment',
          chessTier: 'ROCK',
          displayOrder: 0,
          actualStatus: 'DONE',
        },
        {
          id: 'c-p',
          planId: FLAGGED_PLAN.id,
          title: 'A pebble',
          supportingOutcomeId: 'so_alignment',
          chessTier: 'PEBBLE',
          displayOrder: 1,
          actualStatus: 'PARTIAL',
        },
      ],
      meta: {},
    },
  }).as('listCommits');
  cy.intercept('POST', `/api/v1/plans/${FLAGGED_PLAN.id}/reviews`, {
    statusCode: 201,
    body: {
      data: { id: 'review-1', planId: FLAGGED_PLAN.id, managerId: 'mgr-1' },
      meta: {},
    },
  }).as('createReview');
});

Given('the manager visits the weekly-commit team route', () => {
  cy.visit('/#/weekly-commit/team');
});

Then('the alignment percentage is visible', () => {
  cy.get('[data-testid="team-rollup-stats"]').should('contain.text', '91%');
});

Then('the completion percentage is visible', () => {
  cy.get('[data-testid="team-rollup-stats"]').should('contain.text', '76%');
});

Then('the unreviewed count is visible', () => {
  cy.get('[data-testid="team-rollup-stats"]').should('contain.text', '3');
});

Then('the stuck-commit count is visible', () => {
  cy.get('[data-testid="team-rollup-stats"]').should('contain.text', '2');
});

Then('the first member card has at least one flag', () => {
  cy.get('[data-testid="member-card"]')
    .first()
    .find('[data-testid="member-card-flags"]')
    .should('exist');
});

Then('the unflagged members appear after the flagged ones', () => {
  cy.get('[data-testid="member-card"]').then(($cards) => {
    // First card has flags; subsequent cards have none. Asserting strictly.
    expect($cards.eq(0).find('[data-testid="member-card-flags"]').length, 'flagged first').to.equal(
      1,
    );
    expect(
      $cards.eq(1).find('[data-testid="member-card-flags"]').length,
      'unflagged second',
    ).to.equal(0);
    expect(
      $cards.eq(2).find('[data-testid="member-card-flags"]').length,
      'unflagged third',
    ).to.equal(0);
  });
});

When('I click the first member card', () => {
  cy.get('[data-testid="member-card"]').first().click();
});

Then('the IC drawer is visible', () => {
  cy.get('[role="dialog"][aria-modal="true"]').should('be.visible');
});

Then('the drawer shows the full reflection note', () => {
  cy.get('[data-testid="ic-drawer-reflection"]').should(
    'contain.text',
    'Full reflection note shown only inside the IC drawer.',
  );
});

Then('the drawer shows the commit list grouped by chess tier', () => {
  cy.get('[role="dialog"]').find('[role="group"]').should('have.length', 3);
});

When('I type {string} into the comment field', (text: string) => {
  cy.get('[role="textbox"][aria-label="Comment"]').type(text);
});

When('I click the {string} button', (label: string) => {
  cy.contains('button', label).click();
});

// RegExp instead of cucumber-expression to dodge the "alternative may not be empty" parse
// error from `/plans/reviews` (slashes become alternation tokens, leaving empty alternatives).
Then(/^the API receives POST \/plans\/reviews$/, () => {
  cy.wait('@createReview');
});

Then('the {string} indicator is visible', (label: string) => {
  cy.contains(label).should('be.visible');
});

When('I press the Escape key', () => {
  cy.get('body').type('{esc}');
});

Then('the IC drawer is no longer visible', () => {
  cy.get('[role="dialog"][aria-modal="true"]').should('not.exist');
});
