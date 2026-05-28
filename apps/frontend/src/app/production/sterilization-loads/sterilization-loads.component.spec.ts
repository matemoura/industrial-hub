import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { SterilizationLoadsComponent } from './sterilization-loads.component';
import {
  SterilizationLoadsService,
  SterilizationLoadSummary,
  PendingOrder,
} from '../sterilization-loads.service';
import { AuthService } from '../../auth/auth.service';

// ---- Mock data ----

const makeLoad = (overrides: Partial<SterilizationLoadSummary> = {}): SterilizationLoadSummary => ({
  id: 'uuid-1',
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
  totalOrders: 0,
  ...overrides,
});

const mockOpenLoad = makeLoad();
const mockReleasedLoad = makeLoad({
  id: 'uuid-2',
  loadNumber: 'CARGA-2026-002',
  status: 'RELEASED',
  method: 'GAMMA',
  releasedAt: '2026-05-29T14:00:00',
});

const mockPendingOrder: PendingOrder = {
  id: 'op-uuid-1',
  dynamicsOrderNumber: 'OP-2026-0001',
  productCode: 'P001',
  productName: 'Produto A',
  familyName: 'Família A',
  plannedQty: 100,
  dueDate: '2030-12-31',
  overdue: false,
};

const mockOverduePendingOrder: PendingOrder = {
  id: 'op-uuid-2',
  dynamicsOrderNumber: 'OP-2026-0002',
  productCode: 'P002',
  productName: 'Produto B',
  familyName: null,
  plannedQty: 50,
  dueDate: '2020-01-01',
  overdue: true,
};

// ---- Helpers ----

function createComponent(role: string = 'SUPERVISOR') {
  const mockService = {
    listLoads: vi.fn().mockReturnValue(of({ content: [mockOpenLoad], totalElements: 1, totalPages: 1, number: 0 })),
    getPendingOrders: vi.fn().mockReturnValue(of([mockPendingOrder])),
    addOrder: vi.fn().mockReturnValue(of(undefined)),
    createLoad: vi.fn().mockReturnValue(of(mockOpenLoad)),
    removeOrder: vi.fn().mockReturnValue(of(undefined)),
    transitionStatus: vi.fn().mockReturnValue(of(mockOpenLoad)),
  };

  const mockAuth = {
    role: signal(role),
  };

  TestBed.configureTestingModule({
    imports: [SterilizationLoadsComponent],
    providers: [
      { provide: SterilizationLoadsService, useValue: mockService },
      { provide: AuthService, useValue: mockAuth },
      provideRouter([]),
    ],
  });

  const fixture = TestBed.createComponent(SterilizationLoadsComponent);
  const component = fixture.componentInstance;
  return { fixture, component, mockService };
}

// ---- Tests ----

describe('SterilizationLoadsComponent', () => {

  describe('initialization', () => {
    it('should create and call listLoads on init', () => {
      const { fixture, mockService } = createComponent();
      fixture.detectChanges();
      expect(mockService.listLoads).toHaveBeenCalledTimes(1);
    });

    it('should render load cards after loading', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const card = fixture.nativeElement.querySelector('[data-testid="load-card-CARGA-2026-001"]');
      expect(card).toBeTruthy();
    });

    it('should show loading skeleton while loading', () => {
      const { fixture, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0 }).pipe());
      // Before detectChanges, loading = true
      const skeleton = fixture.nativeElement.querySelector('[data-testid="loading-skeleton"]');
      // loading starts as true before init
      expect(fixture.componentInstance.loading()).toBe(true);
    });

    it('should display empty state when no loads', () => {
      const { fixture, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0 }));
      fixture.detectChanges();
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
      expect(empty).toBeTruthy();
    });

    it('should show error banner on load failure', () => {
      const { fixture, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(throwError(() => new Error('network error')));
      fixture.detectChanges();
      const banner = fixture.nativeElement.querySelector('[data-testid="error-banner"]');
      expect(banner).toBeTruthy();
    });
  });

  describe('supervisor access', () => {
    it('should show "Nova Carga" button for SUPERVISOR', () => {
      const { fixture } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-load"]');
      expect(btn).toBeTruthy();
    });

    it('should show "Nova Carga" button for ADMIN', () => {
      const { fixture } = createComponent('ADMIN');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-load"]');
      expect(btn).toBeTruthy();
    });

    it('should NOT show "Nova Carga" button for OPERATOR', () => {
      const { fixture } = createComponent('OPERATOR');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-load"]');
      expect(btn).toBeFalsy();
    });
  });

  describe('create form', () => {
    it('should show create form when "Nova Carga" is clicked', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-load"]');
      btn.click();
      fixture.detectChanges();
      const form = fixture.nativeElement.querySelector('[data-testid="create-form"]');
      expect(form).toBeTruthy();
    });

    it('should call createLoad when form is submitted with method', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.showCreateForm.set(true);
      component.newMethod.set('EO_GAS');
      fixture.detectChanges();
      component.createLoad();
      expect(mockService.createLoad).toHaveBeenCalledWith({ method: 'EO_GAS', notes: undefined });
    });

    it('should NOT call createLoad when method is empty', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.newMethod.set('');
      component.createLoad();
      expect(mockService.createLoad).not.toHaveBeenCalled();
    });

    it('should hide form and reload after successful create', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.showCreateForm.set(true);
      component.newMethod.set('STEAM');
      component.createLoad();
      fixture.detectChanges();
      expect(component.showCreateForm()).toBe(false);
      expect(mockService.listLoads).toHaveBeenCalledTimes(2); // init + after create
    });
  });

  describe('status filter', () => {
    it('should render filter chips', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const chips = fixture.nativeElement.querySelector('[data-testid="filter-chips"]');
      expect(chips).toBeTruthy();
    });

    it('should filter loads by status via computed signal', () => {
      const { fixture, component, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(of({
        content: [mockOpenLoad, mockReleasedLoad],
        totalElements: 2, totalPages: 1, number: 0,
      }));
      fixture.detectChanges();
      component.filterStatus.set('RELEASED');
      fixture.detectChanges();
      const openGroup = fixture.nativeElement.querySelector('[data-testid="group-OPEN"]');
      const releasedGroup = fixture.nativeElement.querySelector('[data-testid="group-RELEASED"]');
      expect(openGroup).toBeFalsy();
      expect(releasedGroup).toBeTruthy();
    });
  });

  describe('allocation panel', () => {
    it('should open allocation panel and load pending orders', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.openAllocationPanel('uuid-1');
      fixture.detectChanges();
      expect(mockService.getPendingOrders).toHaveBeenCalledTimes(1);
      const panel = fixture.nativeElement.querySelector('[data-testid="alloc-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should close allocation panel', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.openAllocationPanel('uuid-1');
      fixture.detectChanges();
      component.closeAllocationPanel();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="alloc-panel"]');
      expect(panel).toBeFalsy();
    });

    it('should show confirm row when requestAddOrder is called', () => {
      const { fixture, component, mockService } = createComponent();
      mockService.getPendingOrders.mockReturnValue(of([mockPendingOrder]));
      fixture.detectChanges();
      component.openAllocationPanel('uuid-1');
      fixture.detectChanges();
      component.requestAddOrder('op-uuid-1');
      fixture.detectChanges();
      expect(component.confirmingOrderId()).toBe('op-uuid-1');
      const confirmRow = fixture.nativeElement.querySelector('[data-testid="confirm-row"]');
      expect(confirmRow).toBeTruthy();
    });

    it('should call addOrder and reload on confirmAddOrder', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.addingOrderToLoadId.set('uuid-1');
      component.confirmingOrderId.set('op-uuid-1');
      component.confirmAddOrder();
      expect(mockService.addOrder).toHaveBeenCalledWith('uuid-1', 'op-uuid-1');
    });

    it('should cancel confirm without calling addOrder', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.confirmingOrderId.set('op-uuid-1');
      component.cancelConfirm();
      expect(component.confirmingOrderId()).toBeNull();
      expect(mockService.addOrder).not.toHaveBeenCalled();
    });

    it('should show overdue tag for overdue pending orders', () => {
      const { fixture, component, mockService } = createComponent();
      mockService.getPendingOrders.mockReturnValue(of([mockOverduePendingOrder]));
      fixture.detectChanges();
      component.openAllocationPanel('uuid-1');
      fixture.detectChanges();
      const overdueTag = fixture.nativeElement.querySelector('.overdue-tag');
      expect(overdueTag).toBeTruthy();
    });
  });

  // US-100 — badge totalOrders
  describe('totalOrders badge', () => {
    it('should show badge with correct count', () => {
      const { fixture, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(of({
        content: [makeLoad({ totalOrders: 3 })],
        totalElements: 1, totalPages: 1, number: 0,
      }));
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="orders-badge-CARGA-2026-001"]');
      expect(badge).toBeTruthy();
      expect(badge.textContent.trim()).toContain('3');
    });

    it('should have inactive style when totalOrders is 0', () => {
      const { fixture, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(of({
        content: [makeLoad({ totalOrders: 0 })],
        totalElements: 1, totalPages: 1, number: 0,
      }));
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="orders-badge-CARGA-2026-001"]');
      expect(badge.classList.contains('load-card__orders-badge--active')).toBe(false);
    });

    it('should have active style when totalOrders > 0', () => {
      const { fixture, mockService } = createComponent();
      mockService.listLoads.mockReturnValue(of({
        content: [makeLoad({ totalOrders: 2 })],
        totalElements: 1, totalPages: 1, number: 0,
      }));
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="orders-badge-CARGA-2026-001"]');
      expect(badge.classList.contains('load-card__orders-badge--active')).toBe(true);
    });
  });

  describe('labels', () => {
    it('should return correct statusLabel', () => {
      const { component } = createComponent();
      expect(component.statusLabel('OPEN')).toBe('Aberta');
      expect(component.statusLabel('CLOSED')).toBe('Fechada');
      expect(component.statusLabel('STERILIZING')).toBe('Esterilizando');
      expect(component.statusLabel('RELEASED')).toBe('Liberada');
      expect(component.statusLabel('REJECTED')).toBe('Rejeitada');
    });

    it('should return correct methodLabel', () => {
      const { component } = createComponent();
      expect(component.methodLabel('EO_GAS')).toBe('Óxido de Etileno');
      expect(component.methodLabel('GAMMA')).toBe('Radiação Gama');
      expect(component.methodLabel('STEAM')).toBe('Vapor');
      expect(component.methodLabel('OTHER')).toBe('Outro');
      expect(component.methodLabel(null)).toBe('—');
    });
  });
});
