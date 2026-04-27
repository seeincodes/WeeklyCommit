@pending
Feature: Commit entry — IC starts the week (USER_FLOW.md flow 1)
  As an Individual Contributor opening /weekly-commit/current on Monday
  I want to start a fresh weekly plan from the explicit blank state
  So that I plan intentionally rather than by side-effect of route navigation

  Background:
    Given the IC has no plan for the current week
    And the IC visits the weekly-commit current-week route

  Scenario: Blank-state empty CTA renders before any plan exists
    Then the blank state is visible
    And the "Create plan" button is visible

  Scenario: Clicking Create plan transitions into the DRAFT editor
    When I click the "Create plan" button
    Then the DRAFT editor is visible
    And the new plan has state "DRAFT"

  Scenario: Picking an RCDO outcome populates the picker selection
    When I click the "Create plan" button
    And I open the RCDO picker
    And I select the supporting outcome "Alignment tooling GA"
    Then the supporting outcome "Alignment tooling GA" is selected
    And the breadcrumb "Unblock product-led growth › Product-led GTM › Tooling readiness › Alignment tooling GA" is visible
