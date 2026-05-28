import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ProductionOrdersComponent } from './production-orders.component';
import { ProductionService } from '../production.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const mockStockItems = [
  {
    id: 's1',
    product: { id: 'p1', dynamicsCode: 'P001', name: 'Produto A', description: '', family: null as never, active: true },
    qty: 100,
    unit: 'UN',
    snapshotDate: '2026-05-01',
  },
];

const mockOrders = [
  {
    id: 'o1',
    dynamicsOrderNumber: 'OP-001',
    product: { id: 'p1', dynamicsCode: 'P001', name: 'Produto A', description: '', family: null as never, active: true },
    status: 'IN_PROGRESS' as const,
    plannedQty: 200,
    producedQty: 100,
    startDate: '2026-05-01',
    dueDate: '2026-05-31',
    plannedPeople: 3,
    peopleOverridden: false,
  },
  {
    id: 'o2',
    dynamicsOrderNumber: 'OP-002',
    product: { id: 'p1', dynamicsCode: 'P001', name: 'Produto A', description: '', family: null as never, active: true },
    status: 'PLANNED' as const,
    plannedQty: 300,
    producedQty: null,
    startDate: null,
    dueDate: '2026-06-15',
    plannedPeople: null,
    peopleOverridden: false,
  },
];

function makeService() {
  return {
    listStock: vi.fn().mockReturnValue(of(mockStockItems)),
    listOrders: vi.fn().mockReturnValue(of({ content: mockOrders, totalElements: 2, totalPages: 1, number: 0 })),
    importStock: vi.fn(),
    importOrders: vi.fn(),
    listFamilies: vi.fn().mockReturnValue(of([])),
    updateStaffing: vi.fn().mockReturnValue(of({ id: 'o1', dynamicsOrderNumber: 'OP-001', plannedPeople: 5, peopleOverridden: true })),
    resetStaffing: vi.fn().mockReturnValue(of({ id: 'o1', dynamicsOrderNumber: 'OP-001', plannedPeople: 2, peopleOverridden: false })),
  };
}

function makeAuth(role = 'SUPERVISOR') {
  return { role: signal(role) };
}

async function createComponent(role = 'SUPERVISOR') {
  const svc = makeService();
  const auth = makeAuth(role);
  await TestBed.configureTestingModule({
    imports: [ProductionOrdersComponent],
    providers: [
      { provide: ProductionService, useValue: svc },
      { provide: AuthService, useValue: auth },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(ProductionOrdersComponent);
  fixture.detectChanges();
  return { fixture, svc, auth };
}

describe('ProductionOrdersComponent', () => {
  describe('Stock tab', () => {
    it('renders stock table with data', async () => {
      const { fixture } = await createComponent();
      await fixture.whenStable();
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('[data-testid="stock-row"]');
      expect(rows.length).toBe(1);
      expect(fixture.nativeElement.querySelector('[data-testid="stock-qty"]').textContent.trim()).toBe('100');
    });

    it('shows import stock button for SUPERVISOR', async () => {
      const { fixture } = await createComponent('SUPERVISOR');
      await fixture.whenStable();
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-stock"]');
      expect(btn).not.toBeNull();
    });

    it('hides import stock button for OPERATOR', async () => {
      const { fixture } = await createComponent('OPERATOR');
      await fixture.whenStable();
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-stock"]');
      expect(btn).toBeNull();
    });

    it('calls importStock and shows result after upload', async () => {
      const mockResult = { imported: 3, updated: 1, skipped: 0, errors: [] };
      const { fixture, svc } = await createComponent('SUPERVISOR');
      svc.importStock.mockReturnValue(of(mockResult));
      await fixture.whenStable();
      fixture.detectChanges();

      const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-stock"]');
      importBtn.click();
      fixture.detectChanges();

      const comp = fixture.componentInstance;
      comp.stockSelectedFile.set(new File(['data'], 'stock.xlsx'));
      fixture.detectChanges();

      const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-stock-upload"]');
      confirmBtn.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(svc.importStock).toHaveBeenCalled();
      const result = fixture.nativeElement.querySelector('[data-testid="stock-import-result"]');
      expect(result).not.toBeNull();
      const importedCard = fixture.nativeElement.querySelector('[data-testid="stock-result-imported"]');
      expect(importedCard.textContent).toContain('3');
    });

    it('shows error on stock import failure', async () => {
      const { fixture, svc } = await createComponent('SUPERVISOR');
      svc.importStock.mockReturnValue(throwError(() => ({ error: { message: 'Erro de formato' } })));
      await fixture.whenStable();
      fixture.detectChanges();

      const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-stock"]');
      importBtn.click();
      fixture.detectChanges();

      fixture.componentInstance.stockSelectedFile.set(new File(['data'], 'stock.xlsx'));
      fixture.detectChanges();

      const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-stock-upload"]');
      confirmBtn.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const errEl = fixture.nativeElement.querySelector('[data-testid="stock-upload-error"]');
      expect(errEl).not.toBeNull();
      expect(errEl.textContent).toContain('Erro de formato');
    });
  });

  describe('Orders tab', () => {
    it('renders orders table after switching to orders tab', async () => {
      const { fixture } = await createComponent();
      await fixture.whenStable();
      fixture.detectChanges();

      const tabBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="tab-orders"]');
      tabBtn.click();
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('[data-testid="order-row"]');
      expect(rows.length).toBe(2);
    });

    it('shows status chips with correct classes', async () => {
      const { fixture } = await createComponent();
      await fixture.whenStable();
      fixture.detectChanges();

      const tabBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="tab-orders"]');
      tabBtn.click();
      fixture.detectChanges();

      const chips = fixture.nativeElement.querySelectorAll('[data-testid="order-status-chip"]');
      expect(chips[0].classList.contains('chip--blue')).toBe(true);   // IN_PROGRESS
      expect(chips[1].classList.contains('chip--grey')).toBe(true);   // PLANNED
    });

    it('shows import orders button for SUPERVISOR', async () => {
      const { fixture } = await createComponent('SUPERVISOR');
      await fixture.whenStable();
      fixture.detectChanges();

      const tabBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="tab-orders"]');
      tabBtn.click();
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-orders"]');
      expect(btn).not.toBeNull();
    });

    it('calls importOrders and shows result after upload', async () => {
      const mockResult = { imported: 5, updated: 2, skipped: 0, errors: [] };
      const { fixture, svc } = await createComponent('ADMIN');
      svc.importOrders.mockReturnValue(of(mockResult));
      await fixture.whenStable();
      fixture.detectChanges();

      const tabBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="tab-orders"]');
      tabBtn.click();
      fixture.detectChanges();

      const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-orders"]');
      importBtn.click();
      fixture.detectChanges();

      fixture.componentInstance.ordersSelectedFile.set(new File(['data'], 'orders.xlsx'));
      fixture.detectChanges();

      const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-orders-upload"]');
      confirmBtn.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(svc.importOrders).toHaveBeenCalled();
      const result = fixture.nativeElement.querySelector('[data-testid="orders-import-result"]');
      expect(result).not.toBeNull();
      const importedCard = fixture.nativeElement.querySelector('[data-testid="orders-result-imported"]');
      expect(importedCard.textContent).toContain('5');
    });

    it('shows error on orders import failure', async () => {
      const { fixture, svc } = await createComponent('ADMIN');
      svc.importOrders.mockReturnValue(throwError(() => ({ error: { message: 'Formato inválido' } })));
      await fixture.whenStable();
      fixture.detectChanges();

      const tabBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="tab-orders"]');
      tabBtn.click();
      fixture.detectChanges();

      const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-orders"]');
      importBtn.click();
      fixture.detectChanges();

      fixture.componentInstance.ordersSelectedFile.set(new File(['data'], 'orders.xlsx'));
      fixture.detectChanges();

      const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-orders-upload"]');
      confirmBtn.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const errEl = fixture.nativeElement.querySelector('[data-testid="orders-upload-error"]');
      expect(errEl).not.toBeNull();
      expect(errEl.textContent).toContain('Formato inválido');
    });
  });

  // US-086 — staffing inline edit
  describe('staffing inline edit', () => {
    it('should show inline input when edit button is clicked', async () => {
      const { fixture } = await createComponent('SUPERVISOR');
      const component = fixture.componentInstance;
      component.setTab('orders'); // switch to orders tab first
      component.openStaffingEdit(mockOrders[0] as never);
      fixture.detectChanges();
      const input = fixture.nativeElement.querySelector('[data-testid="staffing-input"]');
      expect(input).toBeTruthy();
    });

    it('should restore display after cancelStaffingEdit', async () => {
      const { fixture } = await createComponent('SUPERVISOR');
      const component = fixture.componentInstance;
      component.setTab('orders');
      component.openStaffingEdit(mockOrders[0] as never);
      component.cancelStaffingEdit();
      fixture.detectChanges();
      expect(component.staffingEdit()).toBeNull();
      const input = fixture.nativeElement.querySelector('[data-testid="staffing-input"]');
      expect(input).toBeFalsy();
    });

    it('should call updateStaffing when save is clicked', async () => {
      const { fixture, svc } = await createComponent('SUPERVISOR');
      const component = fixture.componentInstance;
      component.openStaffingEdit(mockOrders[0] as never);
      component.staffingEdit.set({ orderId: 'o1', inputValue: 5, saving: false, confirmReset: false });
      component.saveStaffing();
      expect(svc.updateStaffing).toHaveBeenCalledWith('o1', 5);
    });

    it('should show reset icon only when peopleOverridden is true', async () => {
      const { fixture, svc } = await createComponent('SUPERVISOR');
      const component = fixture.componentInstance;
      component.setTab('orders');
      (svc.listOrders as ReturnType<typeof vi.fn>).mockReturnValue(of({
        content: [{ ...mockOrders[0], peopleOverridden: true }],
        totalElements: 1, totalPages: 1, number: 0,
      }));
      component.loadOrders();
      fixture.detectChanges();
      const resetBtn = fixture.nativeElement.querySelector(`[data-testid="btn-reset-staffing-o1"]`);
      expect(resetBtn).toBeTruthy();
    });

    it('should call resetStaffing after confirmResetStaffing', async () => {
      const { fixture, svc } = await createComponent('SUPERVISOR');
      const component = fixture.componentInstance;
      component.requestResetStaffing('o1');
      component.confirmResetStaffing();
      expect(svc.resetStaffing).toHaveBeenCalledWith('o1');
    });
  });
});
