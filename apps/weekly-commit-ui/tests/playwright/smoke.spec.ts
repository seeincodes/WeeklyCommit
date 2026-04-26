import { expect, test } from '@playwright/test';

test.describe('weekly-commit smoke', () => {
  test('current-week route renders with version stamp', async ({ page }) => {
    await page.goto('/#/weekly-commit/current');

    const root = page.getByTestId('current-week-page');
    await expect(root).toBeVisible();

    const heading = root.getByRole('heading', { name: /weekly commit/i });
    await expect(heading).toBeVisible();

    const version = page.getByTestId('version');
    await expect(version).toContainText(/Build:/);
  });

  test('bare /weekly-commit redirects into the current-week route', async ({ page }) => {
    // Federation handshake regression guard: nested route navigation must mount the
    // module without hook errors (ADR-0003 shared-singleton drift would surface here).
    await page.goto('/#/weekly-commit');
    await expect(page.getByTestId('current-week-page')).toBeVisible();
  });

  test('history route mounts', async ({ page }) => {
    await page.goto('/#/weekly-commit/history');
    await expect(page.getByTestId('history-page')).toBeVisible();
  });
});
