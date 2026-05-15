import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';

test.describe('Authentication', () => {
  test('login with valid credentials redirects to /dashboard', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('operator', 'operator');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('login with invalid credentials shows inline error', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('operator', 'wrong-password');
    await expect(loginPage.errorMessage()).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('protected route without token redirects to /login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('logout clears session and redirects to /login', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('operator', 'operator');
    await expect(page).toHaveURL(/\/dashboard/);
    await page.click('[data-testid="logout-btn"]');
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });
});
