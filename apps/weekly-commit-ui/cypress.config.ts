import { defineConfig } from 'cypress';
import { addCucumberPreprocessorPlugin } from '@badeball/cypress-cucumber-preprocessor';
import createBundler from '@bahmutov/cypress-esbuild-preprocessor';
import { createEsbuildPlugin } from '@badeball/cypress-cucumber-preprocessor/esbuild';

export default defineConfig({
  e2e: {
    // Cucumber .feature files. Per CLAUDE.md tech-stack lock + Common-Issues
    // note about step collisions, step files are scoped per-feature in
    // `cypress/support/step_definitions/<feature-name>.ts` (configured in
    // package.json `cypress-cucumber-preprocessor.stepDefinitions`).
    specPattern: 'cypress/e2e/**/*.feature',
    supportFile: 'cypress/support/e2e.ts',
    baseUrl: 'http://localhost:4184', // matches the Vite dev server in vite.config.ts
    // Subtask 5 may extend this with `webServer`-style config; for now we
    // assume the dev server (or a docker-compose stack) is already up.
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
