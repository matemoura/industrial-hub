import { Page } from '@playwright/test';

export class ImportsPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/summary');
  }

  fileInput() {
    return this.page.locator('input[type="file"]');
  }

  async uploadFile(filePath: string) {
    await this.fileInput().setInputFiles(filePath);
    await this.page.click('[data-testid="import-btn"]');
  }

  successMessage() {
    return this.page.locator('[data-testid="import-success"]');
  }

  errorMessage() {
    return this.page.locator('[data-testid="import-error"]');
  }
}
