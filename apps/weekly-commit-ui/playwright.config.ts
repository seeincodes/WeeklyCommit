import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for the weekly-commit remote in standalone-isolation mode.
 *
 * Boots the Vite dev server on port 4184 (the same port vite.config.ts pins) and runs a
 * minimal smoke against the placeholder route. The federated-inside-host flow is covered
 * separately by the Cypress + Cucumber suite -- see CLAUDE.md tech-stack lock.
 *
 * Keep this lean: per group 9 scope, the goal is a working skeleton, not coverage of
 * actual product surfaces. Real assertions land alongside the IC/manager/admin UIs in
 * groups 11-13.
 */
export default defineConfig({
  testDir: './tests/playwright',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4184',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'yarn dev',
    url: 'http://localhost:4184',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
