// @ts-check
import { defineConfig } from 'cypress';
import { addCucumberPreprocessorPlugin } from '@badeball/cypress-cucumber-preprocessor';
import createBundler from '@bahmutov/cypress-esbuild-preprocessor';
import { createEsbuildPlugin } from '@badeball/cypress-cucumber-preprocessor/esbuild';

// .mjs (explicitly ESM) on purpose. Two CI failure modes ruled out the alternatives:
//   1. .ts -- Cypress 13's packaged Node runtime can't transpile it
//      (ERR_UNKNOWN_FILE_EXTENSION).
//   2. .js + package.json "type":"module" -- Cypress's plugin loader uses
//      require(), which rejects ES-module .js with "require() of ES Module ... not
//      supported".
// .mjs is always-ESM regardless of package.json, so Cypress's loader routes to its
// dynamic-import path. JSDoc `@ts-check` + the imports gives us TypeScript intellisense
// without the runtime cost.

export default defineConfig({
  e2e: {
    // Cucumber .feature files. Per CLAUDE.md tech-stack lock + Common-Issues note about
    // step collisions, step files are scoped per-feature in
    // `cypress/support/step_definitions/<feature-name>.ts` (configured in package.json
    // `cypress-cucumber-preprocessor.stepDefinitions`).
    specPattern: 'cypress/e2e/**/*.feature',
    supportFile: 'cypress/support/e2e.ts',
    baseUrl: 'http://localhost:4184', // matches the Vite dev server in vite.config.ts
    // Default-skip @pending scenarios. The four scenario .feature files (commit-entry,
    // lock-week, reconcile, manager-review) and the kill-switch contract scenarios are
    // all @pending until group 13b lands the WeekEditor mode-pane integration. CI
    // re-enables them by setting CYPRESS_TAGS='' once 13b is merged.
    env: {
      TAGS: 'not @pending',
    },
    async setupNodeEvents(on, config) {
      await addCucumberPreprocessorPlugin(on, config);
      on(
        'file:preprocessor',
        createBundler({
          plugins: [createEsbuildPlugin(config)],
        }),
      );
      return config;
    },
  },
});
