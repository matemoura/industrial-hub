import { Page } from '@playwright/test';

export class MaintenancePage {
  constructor(private readonly page: Page) {}

  async gotoEquipmentList() {
    await this.page.goto('/maintenance/equipment');
  }

  async gotoNewEquipment() {
    await this.page.goto('/maintenance/equipment/new');
  }

  async fillEquipmentForm(code: string, name: string, type: string) {
    await this.page.fill('[data-testid="equipment-code"]', code);
    await this.page.fill('[data-testid="equipment-name"]', name);
    await this.page.selectOption('[data-testid="equipment-type"]', type);
  }

  async submitEquipmentForm() {
    await this.page.click('[data-testid="equipment-submit"]');
  }

  async clickFirstEquipmentRow() {
    await this.page.locator('[data-testid="equipment-row"]').first().click();
  }

  async createWorkOrder(type: string, title: string, priority: string) {
    await this.page.click('[data-testid="btn-new-work-order"]');
    await this.page.selectOption('[data-testid="wo-type"]', type);
    await this.page.fill('[data-testid="wo-title"]', title);
    await this.page.selectOption('[data-testid="wo-priority"]', priority);
    await this.page.click('[data-testid="wo-submit"]');
  }

  async transitionWorkOrderTo(targetStatus: string) {
    await this.page.click(`[data-testid="btn-wo-${targetStatus.toLowerCase()}"]`);
    await this.page.click('[data-testid="confirm-dialog-ok"]');
  }

  equipmentStatusChip() {
    return this.page.locator('[data-testid="equipment-status-chip"]');
  }

  workOrderStatusChip() {
    return this.page.locator('[data-testid="wo-status-chip"]').first();
  }
}
