@pending @host-contract
Feature: Kill switch — host-app feature flag bypasses the remote (PRD [MVP24])
  As the org rolling out / rolling back the Weekly Commit module
  I want the host app to flip a feature flag and replace the remote with a 15-Five link
  So that we can disable the module instantly without redeploying it

  Background:
    Given the host app is configured with a kill-switch feature flag

  Scenario: When the flag is ON, the host loads the WeeklyCommit remote
    Given the host kill-switch flag is "on"
    When a user opens the host app's /weekly-commit route
    Then the host app loads the federated remote
    And the WeeklyCommit module is visible

  Scenario: When the flag is OFF, the host renders the 15-Five fallback link
    Given the host kill-switch flag is "off"
    When a user opens the host app's /weekly-commit route
    Then the host app does not load the federated remote
    And a "Continue in 15-Five" link is visible
    And the link target points to the configured 15-Five URL

  Scenario: Flag flip propagates without a remote redeploy
    Given the host kill-switch flag is "on"
    And the user is mid-session in the WeeklyCommit module
    When the host kill-switch flag flips to "off"
    And the user navigates back to /weekly-commit
    Then the host app renders the 15-Five fallback link instead of remounting the remote
