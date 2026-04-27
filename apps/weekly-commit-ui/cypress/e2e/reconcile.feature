@pending
Feature: Reconcile — IC closes the week on Friday (USER_FLOW.md flow 3)
  As an IC whose plan is LOCKED past day 4
  I want to reconcile each commit and submit a reflection
  So that the manager rollup reflects what actually happened

  Background:
    Given the IC has a LOCKED plan with weekStart >= 4 days ago and commits a, b, c
    And the IC visits the weekly-commit current-week route

  Scenario: Reconciliation table renders for each commit
    Then the reconciliation table is visible
    And each commit row has DONE / PARTIAL / MISSED radios

  Scenario: Marking a commit as DONE persists via PATCH
    When I select the "DONE" status for commit "a"
    Then the API receives PATCH /commits/a with actualStatus DONE

  Scenario: Reflection note saves on edit and shows the char counter
    When I type a 200-character reflection note
    Then the reflection counter reads "200/500"
    And the API receives PATCH /plans with the reflection body

  Scenario: Reflection counter flips to warning style at 480+ chars
    When I type a 480-character reflection note
    Then the reflection counter has the warning style

  Scenario: Carrying a missed commit forward creates a twin in next week's DRAFT
    Given commit "b" was reconciled MISSED
    When I click the "Carry to next week" button on commit "b"
    Then the API receives POST /commits/b/carry-forward

  Scenario: Submit reconciliation transitions to RECONCILED
    When I click the "Submit reconciliation" button
    Then the API receives POST /plans/transitions with target RECONCILED
    And the plan state badge reads "RECONCILED"
