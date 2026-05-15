import { Page } from '@playwright/test';

export class NcPage {
  constructor(private readonly page: Page) {}

  async gotoList() {
    await this.page.goto('/qms/non-conformances');
  }

  async gotoNew() {
    await this.page.goto('/qms/non-conformances/new');
  }

  async fillNewNcForm(title: string, type: string, severity: string) {
    await this.page.fill('[data-testid="nc-title"]', title);
    await this.page.selectOption('[data-testid="nc-type"]', type);
    await this.page.selectOption('[data-testid="nc-severity"]', severity);
  }

  async submitNcForm() {
    await this.page.click('[data-testid="nc-submit"]');
  }

  async clickFirstNcRow() {
    await this.page.locator('[data-testid="nc-row"]').first().click();
  }

  async transitionToInAnalysis() {
    await this.page.click('[data-testid="btn-start-analysis"]');
    await this.page.click('[data-testid="confirm-dialog-ok"]');
  }

  async transitionToClose() {
    await this.page.click('[data-testid="btn-close-nc"]');
    await this.page.click('[data-testid="confirm-dialog-ok"]');
  }

  statusChip() {
    return this.page.locator('[data-testid="nc-status-chip"]');
  }

  ncRows() {
    return this.page.locator('[data-testid="nc-row"]');
  }
}
