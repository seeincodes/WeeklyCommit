import { Given, When, Then } from '@badeball/cypress-cucumber-preprocessor';

// ---------------------------------------------------------------------------
// kill-switch.feature -- HOST-CONTRACT step bindings.
//
// These steps ASSUME a host-app harness that this repo doesn't ship -- the
// kill switch is a host-side concern per PRD [MVP24]. Each step throws a
// clear "host harness required" error if invoked, so anyone running the
// suite without the harness sees exactly what's missing rather than an
// opaque selector failure.
//
// The .feature is @pending-tagged so the default `cypress run` skips it
// entirely; the host-app team's cross-remote E2E job is the place where
// CYPRESS_TAGS gets overridden to include @host-contract scenarios.
//
// See TASK_LIST group 14 ("Kill-switch feature flag plumbed through host-app
// config") for the host-side implementation owner.
// ---------------------------------------------------------------------------

function requireHostHarness(stepName: string): never {
  throw new Error(
    `[host-harness-required] step "${stepName}" needs the cross-remote test harness ` +
      'that loads the PA host app with a configurable kill-switch flag. The harness ' +
      'lives in the host-app repo (TASK_LIST group 14). Until then this scenario ' +
      'stays @pending; default cypress run skips it.',
  );
}

Given('the host app is configured with a kill-switch feature flag', () => {
  requireHostHarness('the host app is configured with a kill-switch feature flag');
});

Given('the host kill-switch flag is {string}', (_state: string) => {
  requireHostHarness('the host kill-switch flag is "<state>"');
});

When("a user opens the host app's /weekly-commit route", () => {
  requireHostHarness("a user opens the host app's /weekly-commit route");
});

Then('the host app loads the federated remote', () => {
  requireHostHarness('the host app loads the federated remote');
});

Then('the WeeklyCommit module is visible', () => {
  requireHostHarness('the WeeklyCommit module is visible');
});

Then('the host app does not load the federated remote', () => {
  requireHostHarness('the host app does not load the federated remote');
});

Then('a {string} link is visible', (_label: string) => {
  requireHostHarness('a "<label>" link is visible');
});

Then('the link target points to the configured 15-Five URL', () => {
  requireHostHarness('the link target points to the configured 15-Five URL');
});

Given('the user is mid-session in the WeeklyCommit module', () => {
  requireHostHarness('the user is mid-session in the WeeklyCommit module');
});

When('the host kill-switch flag flips to {string}', (_state: string) => {
  requireHostHarness('the host kill-switch flag flips to "<state>"');
});

When('the user navigates back to /weekly-commit', () => {
  requireHostHarness('the user navigates back to /weekly-commit');
});

Then('the host app renders the 15-Five fallback link instead of remounting the remote', () => {
  requireHostHarness(
    'the host app renders the 15-Five fallback link instead of remounting the remote',
  );
});
