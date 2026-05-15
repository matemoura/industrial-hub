import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { ImportsPage } from '../pages/imports.page';
import path from 'path';

test.describe('OEE Import', () => {
  test.beforeEach(async ({ page }) => {
    await new LoginPage(page).login('supervisor', 'supervisor');
  });

  test('upload invalid file shows error message', async ({ page }) => {
    const importsPage = new ImportsPage(page);
    await importsPage.goto();
    const invalidFile = path.join(__dirname, '../fixtures/invalid.txt');
    await importsPage.uploadFile(invalidFile);
    await expect(importsPage.errorMessage()).toBeVisible();
  });

  test('summary page loads with OEE data cards', async ({ page }) => {
    await page.goto('/summary');
    await expect(page.locator('[data-testid="oee-summary-card"]').first()).toBeVisible();
  });
});
