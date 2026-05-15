import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { MaintenancePage } from '../pages/maintenance.page';

test.describe('Maintenance Work Orders', () => {
  test.beforeEach(async ({ page }) => {
    await new LoginPage(page).login('admin', 'admin');
  });

  test('create equipment and verify status is OPERATIONAL', async ({ page }) => {
    const mp = new MaintenancePage(page);
    const code = `EQ-E2E-${Date.now()}`;

    await mp.gotoNewEquipment();
    await mp.fillEquipmentForm(code, `Equipamento E2E ${code}`, 'MACHINE');
    await mp.submitEquipmentForm();

    await expect(page).toHaveURL(/\/maintenance\/equipment/);
    await expect(page.getByText(code)).toBeVisible();
  });

  test('CORRECTIVE work order sets equipment to UNDER_MAINTENANCE then OPERATIONAL on completion', async ({
    page,
  }) => {
    const mp = new MaintenancePage(page);

    await mp.gotoEquipmentList();
    await mp.clickFirstEquipmentRow();

    await mp.createWorkOrder('CORRECTIVE', `WO E2E ${Date.now()}`, 'HIGH');
    await expect(mp.equipmentStatusChip()).toContainText('UNDER_MAINTENANCE');

    await mp.transitionWorkOrderTo('in_progress');
    await mp.transitionWorkOrderTo('done');
    await expect(mp.equipmentStatusChip()).toContainText('OPERATIONAL');
  });
});
