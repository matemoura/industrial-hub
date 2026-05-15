import { Page } from '@playwright/test';

export class DashboardPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/dashboard');
  }

  kpiCard(label: string) {
    return this.page.locator(`[data-testid="kpi-card-${label}"]`);
  }

  allKpiCards() {
    return this.page.locator('[data-testid^="kpi-card-"]');
  }
}
