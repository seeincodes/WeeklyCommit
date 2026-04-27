@pending
Feature: Manager review — manager closes the loop (USER_FLOW.md flow 4)
  As a Manager opening /weekly-commit/team on Monday morning
  I want to see my team's rollup and review individual plans
  So that I can spot stuck work and acknowledge reflections at scale

  Background:
    Given the manager has 3 direct reports with one flagged member
    And the manager visits the weekly-commit team route

  Scenario: Team rollup renders the aggregate stats strip
    Then the alignment percentage is visible
    And the completion percentage is visible
    And the unreviewed count is visible
    And the stuck-commit count is visible

  Scenario: Flagged members sort to the top of the rollup
    Then the first member card has at least one flag
    And the unflagged members appear after the flagged ones

  Scenario: Clicking a member card opens the IC drawer
    When I click the first member card
    Then the IC drawer is visible
    And the drawer shows the full reflection note
    And the drawer shows the commit list grouped by chess tier

  Scenario: Submitting a comment acknowledges the plan
    When I click the first member card
    And I type "Nice Top Rock pick" into the comment field
    And I click the "Acknowledge" button
    Then the API receives POST /plans/reviews
    And the "Acknowledged on" indicator is visible

  Scenario: Pressing Escape dismisses the drawer
    When I click the first member card
    And I press the Escape key
    Then the IC drawer is no longer visible
