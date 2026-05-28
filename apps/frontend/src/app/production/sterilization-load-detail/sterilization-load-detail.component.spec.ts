import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { SterilizationLoadDetailComponent } from './sterilization-load-detail.component';
import {
  SterilizationLoadsService,
  SterilizationLoadDetail,
  AllocatedOrderEntry,
} from '../sterilization-loads.service';
import { AuthService } from '../../auth/auth.service';

// ---- Mock data ----

const makeOrderEntry = (overrides: Partial<AllocatedOrderEntry> = {}): AllocatedOrderEntry => ({
  id: 'op-uuid-1',
  dynamicsOrderNumber: 'OP-2026-0001',
  productCode: 'P001',
  productName: 'Produto A',
  familyName: 'Família A',
  plannedQty: 100,
  dueDate: '2030-12-31',
  overdue: false,
  ...overrides,
});

const makeDetail = (overrides: Partial<SterilizationLoadDetail> = {}): SterilizationLoadDetail => ({
  id: 'load-uuid-1',
  loadNumber: 'CARGA-2026-001',
  status: 'OPEN',
  method: 'EO_GAS',
  sterilizerName: null,
  sterilizationDate: null,
  batchCode: null,
  notes: null,
  createdBy: 'supervisor1',
  createdAt: '2026-05-29T08:00:00',
  closedAt: null,
  releasedAt: null,
  orders: [makeOrderEntry()],
  totalOrders: 1,
  totalPlannedQty: 100,
  ...overrides,
});

// ---- Helpers ----

function createComponent(
  detail: SterilizationLoadDetail = makeDetail(),
  role: string = 'SUPERVISOR',
  serviceError = false,
) {
  const mockService = {
    getLoad: vi.fn().mockReturnValue(serviceError ? throwError(() => new Error()) : of(detail)),
    removeOrder: vi.fn().mockReturnValue(of(undefined)),
    transitionStatus: vi.fn().mockReturnValue(of({ ...detail, status: 'CLOSED' })),
  };

  const mockAuth = { role: signal(role) };

  TestBed.configureTestingModule({
    imports: [SterilizationLoadDetailComponent],
    providers: [
      { provide: SterilizationLoadsService, useValue: mockService },
      { provide: AuthService, useValue: mockAuth },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'load-uuid-1' }) } } },
      provideRouter([]),
    ],
  });

  const fixture = TestBed.createComponent(SterilizationLoadDetailComponent);
  const component = fixture.componentInstance;
  return { fixture, component, mockService };
}

// ---- Tests ----

describe('SterilizationLoadDetailComponent', () => {

  describe('initialization', () => {
    it('should create and call getLoad on init', () => {
      const { fixture, mockService } = createComponent();
      fixture.detectChanges();
      expect(mockService.getLoad).toHaveBeenCalledTimes(1);
    });

    it('should display load number after loading', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="load-number"]');
      expect(el.textContent.trim()).toBe('CARGA-2026-001');
    });

    it('should show error banner on load failure', () => {
      const { fixture } = createComponent(makeDetail(), 'SUPERVISOR', true);
      fixture.detectChanges();
      const banner = fixture.nativeElement.querySelector('[data-testid="error-banner-critical"]');
      expect(banner).toBeTruthy();
    });

    it('should show loading skeleton initially', () => {
      const { fixture, component } = createComponent();
      expect(component.loading()).toBe(true);
    });

    it('should render back link', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const link = fixture.nativeElement.querySelector('[data-testid="back-link"]');
      expect(link).toBeTruthy();
    });
  });

  describe('load info display', () => {
    it('should display method label', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="detail-method"]');
      expect(el.textContent.trim()).toBe('Óxido de Etileno');
    });

    it('should show KPI row with totals', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const totalOrders = fixture.nativeElement.querySelector('[data-testid="kpi-total-orders"]');
      expect(totalOrders.textContent.trim()).toBe('1');
    });

    it('should display orders list', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const list = fixture.nativeElement.querySelector('[data-testid="orders-list"]');
      expect(list).toBeTruthy();
      const item = fixture.nativeElement.querySelector('[data-testid="order-item-OP-2026-0001"]');
      expect(item).toBeTruthy();
    });

    it('should show empty state when no orders', () => {
      const { fixture } = createComponent(makeDetail({ orders: [], totalOrders: 0, totalPlannedQty: 0 }));
      fixture.detectChanges();
      const empty = fixture.nativeElement.querySelector('[data-testid="no-orders"]');
      expect(empty).toBeTruthy();
    });

    it('should display overdue tag for overdue orders', () => {
      const overdueOrder = makeOrderEntry({ overdue: true, dueDate: '2020-01-01' });
      const { fixture } = createComponent(makeDetail({ orders: [overdueOrder] }));
      fixture.detectChanges();
      const overdueTag = fixture.nativeElement.querySelector('.overdue-tag');
      expect(overdueTag).toBeTruthy();
    });
  });

  describe('status transitions', () => {
    it('should show transition button for OPEN → CLOSED', () => {
      const { fixture } = createComponent(makeDetail({ status: 'OPEN' }));
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-transition-CLOSED"]');
      expect(btn).toBeTruthy();
    });

    it('should NOT show transition buttons for RELEASED status', () => {
      const { fixture } = createComponent(makeDetail({ status: 'RELEASED' }));
      fixture.detectChanges();
      const actions = fixture.nativeElement.querySelector('[data-testid="transition-actions"]');
      expect(actions).toBeFalsy();
    });

    it('should NOT show transition buttons for OPERATOR role', () => {
      const { fixture } = createComponent(makeDetail({ status: 'OPEN' }), 'OPERATOR');
      fixture.detectChanges();
      const actions = fixture.nativeElement.querySelector('[data-testid="transition-actions"]');
      expect(actions).toBeFalsy();
    });

    it('should show confirm dialog on requestTransition', () => {
      const { fixture, component } = createComponent(makeDetail({ status: 'OPEN' }));
      fixture.detectChanges();
      component.requestTransition('CLOSED');
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="confirm-dialog"]');
      expect(dialog).toBeTruthy();
    });

    it('should hide confirm dialog on cancelTransition', () => {
      const { fixture, component } = createComponent(makeDetail({ status: 'OPEN' }));
      fixture.detectChanges();
      component.requestTransition('CLOSED');
      component.cancelTransition();
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="confirm-dialog"]');
      expect(dialog).toBeFalsy();
    });

    it('should call transitionStatus on confirmTransition', () => {
      const { fixture, component, mockService } = createComponent(makeDetail({ status: 'OPEN' }));
      fixture.detectChanges();
      component.confirmTransitionTo.set('CLOSED');
      component.confirmTransition();
      expect(mockService.transitionStatus).toHaveBeenCalledWith('load-uuid-1', 'CLOSED');
    });
    // Note: the load id comes from the loaded detail (not from route snapshot which is null in tests with provideRouter)

    it('should show warning message for REJECTED transition', () => {
      const { fixture, component } = createComponent(makeDetail({ status: 'STERILIZING' }));
      fixture.detectChanges();
      component.requestTransition('REJECTED');
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="confirm-dialog"]');
      expect(dialog.textContent).toContain('OPs alocadas serão removidas');
    });

    it('should return correct allowedTransitions for CLOSED status', () => {
      const { component } = createComponent(makeDetail({ status: 'CLOSED' }));
      component.load.set(makeDetail({ status: 'CLOSED' }));
      expect(component.allowedTransitions()).toEqual(['STERILIZING']);
    });

    // AC#15 / AC#17 — RELEASED reminder dialog
    it('should show released reminder dialog after RELEASED transition succeeds', () => {
      const { fixture, component, mockService } = createComponent(makeDetail({ status: 'STERILIZING' }));
      mockService.transitionStatus.mockReturnValue(of({ ...makeDetail(), status: 'RELEASED' }));
      fixture.detectChanges();

      component.confirmTransitionTo.set('RELEASED');
      component.confirmTransition();
      fixture.detectChanges();

      const reminder = fixture.nativeElement.querySelector('[data-testid="released-reminder-dialog"]');
      expect(reminder).toBeTruthy();
      const msg = fixture.nativeElement.querySelector('[data-testid="released-reminder-msg"]');
      expect(msg.textContent).toContain('Dynamics');
    });

    it('should NOT show released reminder for non-RELEASED transitions', () => {
      const { fixture, component, mockService } = createComponent(makeDetail({ status: 'OPEN' }));
      mockService.transitionStatus.mockReturnValue(of({ ...makeDetail(), status: 'CLOSED' }));
      fixture.detectChanges();

      component.confirmTransitionTo.set('CLOSED');
      component.confirmTransition();
      fixture.detectChanges();

      const reminder = fixture.nativeElement.querySelector('[data-testid="released-reminder-dialog"]');
      expect(reminder).toBeFalsy();
    });

    it('should dismiss reminder and reload on dismissReleasedReminder', () => {
      const { fixture, component, mockService } = createComponent(makeDetail({ status: 'STERILIZING' }));
      mockService.transitionStatus.mockReturnValue(of({ ...makeDetail(), status: 'RELEASED' }));
      fixture.detectChanges();

      component.confirmTransitionTo.set('RELEASED');
      component.confirmTransition();
      fixture.detectChanges();

      expect(component.showReleasedReminder()).toBe(true);
      component.dismissReleasedReminder();
      fixture.detectChanges();

      expect(component.showReleasedReminder()).toBe(false);
      // getLoad called: 1 (init) + 1 (after dismiss)
      expect(mockService.getLoad).toHaveBeenCalledTimes(2);
    });
  });

  describe('remove order', () => {
    it('should show confirm row when requestRemoveOrder is called', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.requestRemoveOrder('op-uuid-1');
      fixture.detectChanges();
      const row = fixture.nativeElement.querySelector('[data-testid="remove-confirm-row"]');
      expect(row).toBeTruthy();
    });

    it('should call removeOrder on confirmRemoveOrder', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.removingOrderId.set('op-uuid-1');
      component.confirmRemoveOrder();
      expect(mockService.removeOrder).toHaveBeenCalledWith('load-uuid-1', 'op-uuid-1');
    });

    it('should cancel remove without calling removeOrder', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.removingOrderId.set('op-uuid-1');
      component.cancelRemoveOrder();
      expect(component.removingOrderId()).toBeNull();
      expect(mockService.removeOrder).not.toHaveBeenCalled();
    });

    it('should NOT show remove button for CLOSED load', () => {
      const { fixture } = createComponent(makeDetail({ status: 'CLOSED' }));
      fixture.detectChanges();
      const removeBtn = fixture.nativeElement.querySelector('[data-testid="btn-remove-OP-2026-0001"]');
      expect(removeBtn).toBeFalsy();
    });

    it('should NOT show remove button for OPERATOR role', () => {
      const { fixture } = createComponent(makeDetail({ status: 'OPEN' }), 'OPERATOR');
      fixture.detectChanges();
      const removeBtn = fixture.nativeElement.querySelector('[data-testid="btn-remove-OP-2026-0001"]');
      expect(removeBtn).toBeFalsy();
    });
  });

  describe('labels', () => {
    it('should return correct transitionLabel', () => {
      const { component } = createComponent();
      expect(component.transitionLabel('CLOSED')).toBe('Fechar Carga');
      expect(component.transitionLabel('STERILIZING')).toBe('Iniciar Esterilização');
      expect(component.transitionLabel('RELEASED')).toBe('Liberar');
      expect(component.transitionLabel('REJECTED')).toBe('Rejeitar');
    });

    it('should return correct methodLabel for null', () => {
      const { component } = createComponent();
      expect(component.methodLabel(null)).toBe('—');
    });
  });
});
