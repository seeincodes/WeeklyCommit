Feature: Cypress + Cucumber infra smoke
  Proves the test runner, Cucumber preprocessor, esbuild bundler, and
  step-definition resolution are all wired correctly. The full scenario
  matrix lands in subtasks 2-3 once this baseline is green.

  Scenario: The Vite dev server is reachable from Cypress
    When I visit the weekly-commit current-week route
    Then the current-week page mounts
