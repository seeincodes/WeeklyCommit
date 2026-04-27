@pending
Feature: Lock week — IC commits to the plan (USER_FLOW.md flow 2)
  As an IC who has finished planning
  I want to lock my week
  So that the commits become the source of truth for Friday's reconciliation

  Background:
    Given the IC has a DRAFT plan with at least one commit
    And the IC visits the weekly-commit current-week route

  Scenario: Lock-week transition flips the badge and switches to read-only
    When I click the "Lock Week" button
    Then the plan state badge reads "LOCKED"
    And the editor is in read-only mode pre-day-4
    And the next-action hint reads "Reconciliation opens Friday"

  Scenario: Optimistic lock 409 surfaces the ConflictToast and refetches
    Given a stale version is held client-side
    When I click the "Lock Week" button
    Then the conflict toast is visible
    And the plan refetches automatically
