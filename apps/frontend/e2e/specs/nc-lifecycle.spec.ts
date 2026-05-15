import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { NcPage } from '../pages/nc.page';

test.describe('NC Lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    await new LoginPage(page).login('supervisor', 'supervisor');
  });

  test('create NC and verify it appears in the list', async ({ page }) => {
    const ncPage = new NcPage(page);
    const uniqueTitle = `E2E NC ${Date.now()}`;

    await ncPage.gotoNew();
    await ncPage.fillNewNcForm(uniqueTitle, 'PROCESS', 'HIGH');
    await ncPage.submitNcForm();

    await expect(page).toHaveURL(/\/qms\/non-conformances/);
    await expect(page.getByText(uniqueTitle)).toBeVisible();
  });

  test('transition NC: OPEN → IN_ANALYSIS → CLOSED', async ({ page }) => {
    const ncPage = new NcPage(page);
    const uniqueTitle = `E2E Transition ${Date.now()}`;

    await ncPage.gotoNew();
    await ncPage.fillNewNcForm(uniqueTitle, 'EQUIPMENT', 'MEDIUM');
    await ncPage.submitNcForm();

    await page.getByText(uniqueTitle).click();
    await ncPage.transitionToInAnalysis();
    await expect(ncPage.statusChip()).toContainText('IN_ANALYSIS');

    await ncPage.transitionToClose();
    await expect(ncPage.statusChip()).toContainText('CLOSED');
  });
});
