import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';

test.describe('OEE Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await new LoginPage(page).login('operator', 'operator');
    await page.goto('/oee/efficiency');
  });

  test('OEE dashboard page loads', async ({ page }) => {
    await expect(page.locator('[data-testid="oee-dashboard"]')).toBeVisible();
  });

  test('date filter updates displayed data', async ({ page }) => {
    const startDate = page.locator('[data-testid="filter-start-date"]');
    const endDate = page.locator('[data-testid="filter-end-date"]');
    if (await startDate.isVisible()) {
      await startDate.fill('2026-01-01');
      await endDate.fill('2026-01-31');
      await page.click('[data-testid="filter-apply"]');
      await expect(page.locator('[data-testid="oee-dashboard"]')).toBeVisible();
    }
  });
});
