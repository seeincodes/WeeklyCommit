import { When, Then } from '@badeball/cypress-cucumber-preprocessor';

When('I visit the weekly-commit current-week route', () => {
  // The HashRouter standalone-dev path expects /#/weekly-commit/...
  cy.visit('/#/weekly-commit/current');
});

Then('the current-week page mounts', () => {
  cy.get('[data-testid="current-week-page"]').should('be.visible');
});
