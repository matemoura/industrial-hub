import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';

test.describe('Role-based Access', () => {
  test('OPERATOR does not see SUPERVISOR-only buttons on NC detail', async ({ page }) => {
    await new LoginPage(page).login('operator', 'operator');
    await page.goto('/qms/non-conformances');

    const firstRow = page.locator('[data-testid="nc-row"]').first();
    if (await firstRow.isVisible()) {
      await firstRow.click();
      await expect(page.locator('[data-testid="btn-start-analysis"]')).not.toBeVisible();
      await expect(page.locator('[data-testid="btn-close-nc"]')).not.toBeVisible();
    }
  });

  test('SUPERVISOR sees transition buttons on NC detail', async ({ page }) => {
    await new LoginPage(page).login('supervisor', 'supervisor');
    await page.goto('/qms/non-conformances');

    const firstRow = page.locator('[data-testid="nc-row"]').first();
    if (await firstRow.isVisible()) {
      await firstRow.click();
      const statusChip = page.locator('[data-testid="nc-status-chip"]');
      const statusText = await statusChip.textContent();
      if (statusText?.includes('OPEN')) {
        await expect(page.locator('[data-testid="btn-start-analysis"]')).toBeVisible();
      }
    }
  });

  test('ADMIN sees New Equipment button in maintenance', async ({ page }) => {
    await new LoginPage(page).login('admin', 'admin');
    await page.goto('/maintenance/equipment');
    await expect(page.locator('[data-testid="btn-new-equipment"]')).toBeVisible();
  });

  test('OPERATOR does not see New Equipment button in maintenance', async ({ page }) => {
    await new LoginPage(page).login('operator', 'operator');
    await page.goto('/maintenance/equipment');
    await expect(page.locator('[data-testid="btn-new-equipment"]')).not.toBeVisible();
  });
});
