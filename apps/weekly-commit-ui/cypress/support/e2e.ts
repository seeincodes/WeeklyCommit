// Cypress's per-spec setup hook. Imported once per spec file via the
// `supportFile` option in cypress.config.ts. Runs before any `before()` block
// in the spec, so it's the right place for globals like custom commands.
//
// Empty for now; subtasks 2-4 land custom commands here (e.g. `cy.loginAs(role)`
// for the self-signed-JWT auth helper from subtask 4).
export {};
