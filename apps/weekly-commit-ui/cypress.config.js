// @ts-check
import { defineConfig } from 'cypress';
import { addCucumberPreprocessorPlugin } from '@badeball/cypress-cucumber-preprocessor';
import createBundler from '@bahmutov/cypress-esbuild-preprocessor';
import { createEsbuildPlugin } from '@badeball/cypress-cucumber-preprocessor/esbuild';

// JS (not TS) on purpose: Cypress 13 loads its config in a packaged Node runtime that
// doesn't transpile .ts. Yarn 4 PnP also doesn't auto-resolve a .ts config loader. The
// JSDoc `@ts-check` directive above + the imports gives us TypeScript intellisense
// without the runtime cost. (See ERR_UNKNOWN_FILE_EXTENSION error in the original
// .ts attempt -- group 13 subtask 5 fix-forward.)

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
