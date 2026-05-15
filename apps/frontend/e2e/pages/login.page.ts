import { Page } from '@playwright/test';

export class LoginPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/login');
  }

  async fillCredentials(username: string, password: string) {
    await this.page.fill('[data-testid="username"]', username);
    await this.page.fill('[data-testid="password"]', password);
  }

  async submit() {
    await this.page.click('[data-testid="login-btn"]');
  }

  async login(username: string, password: string) {
    await this.goto();
    await this.fillCredentials(username, password);
    await this.submit();
  }

  errorMessage() {
    return this.page.locator('[data-testid="login-error"]');
  }
}
