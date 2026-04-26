import { expect, test } from '@playwright/test';

test.describe('weekly-commit smoke', () => {
  test('placeholder route renders with version stamp', async ({ page }) => {
    await page.goto('/#/weekly-commit');

    const root = page.getByTestId('weekly-commit-root');
    await expect(root).toBeVisible();

    const heading = root.getByRole('heading', { name: /weekly commit/i });
    await expect(heading).toBeVisible();

    const version = page.getByTestId('version');
    await expect(version).toContainText(/Build:/);
  });

  test('unknown route under /weekly-commit still mounts the module', async ({ page }) => {
    // The placeholder catches everything under /weekly-commit/*. This test guards against
    // the federation handshake failing silently for nested routes -- a problem we hit in
    // PM remote per ADR-0003 context if shared-singleton drift causes hook errors.
    await page.goto('/#/weekly-commit/some/nested/path');
    await expect(page.getByTestId('weekly-commit-root')).toBeVisible();
  });
});
