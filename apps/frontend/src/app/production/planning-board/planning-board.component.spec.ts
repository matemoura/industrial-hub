import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { PlanningBoardComponent } from './planning-board.component';
import {
  FamilyPlanningBoard,
  MrpRunResult,
  PlanningService,
} from '../planning.service';
import { AuthService } from '../../auth/auth.service';

// ---- Mock data ----

const mockBoard: FamilyPlanningBoard[] = [
  {
    familyId: 'fam-uuid-1',
    familyCode: 'FAM-A',
    familyName: 'Família A',
    products: [
      {
        productCode: 'P001', productName: 'Produto A', type: 'FINISHED',
        currentStock: 50, minStockQty: 100, stockSnapshotDate: '2026-05-29',
        openOrdersQty: 30, suggestedOrdersQty: 0, netNeed: 20,
        planningStatus: 'ALERT', totalPlannedPeople: 4, totalOpsOpen: 2,
        leadTimeDays: 7, earliestDueDate: '2026-06-15',
      },
      {
        productCode: 'P002', productName: 'Produto B', type: 'FINISHED',
        currentStock: 200, minStockQty: 100, stockSnapshotDate: '2026-05-29',
        openOrdersQty: 0, suggestedOrdersQty: 0, netNeed: 0,
        planningStatus: 'OK', totalPlannedPeople: 0, totalOpsOpen: 0,
        leadTimeDays: 5, earliestDueDate: null,
      },
    ],
  },
];

const mockDryRunResult: MrpRunResult = {
  run: { id: 'run-1', runAt: '2026-05-29T10:00:00', runBy: 'supervisor1', isDryRun: true, productsAnalyzed: 2, suggestionsGenerated: 1, alreadyOk: 1 },
  suggestions: [
    { id: 'sug-1', productCode: 'P001', productName: 'Produto A', familyName: 'Família A', suggestedQty: 50, adjustedQty: null, suggestedStartDate: null, suggestedDueDate: '2026-06-05', status: 'SUGGESTED', rejectionReason: null },
  ],
  purchaseNeeds: [],
  messages: [],
  isDryRun: true,
};

const mockRunResult: MrpRunResult = {
  ...mockDryRunResult,
  isDryRun: false,
  run: { ...mockDryRunResult.run, isDryRun: false },
};

function createComponent(role = 'SUPERVISOR') {
  const mockService = {
    getPlanningBoard: vi.fn().mockReturnValue(of(mockBoard)),
    getPurchaseNeeds: vi.fn().mockReturnValue(of([])),
    dryRunMrp: vi.fn().mockReturnValue(of(mockDryRunResult)),
    runMrp: vi.fn().mockReturnValue(of(mockRunResult)),
  };
  const mockAuth = { role: signal(role) };

  TestBed.configureTestingModule({
    imports: [PlanningBoardComponent],
    providers: [
      { provide: PlanningService, useValue: mockService },
      { provide: AuthService, useValue: mockAuth },
      provideRouter([]),
    ],
  });

  const fixture = TestBed.createComponent(PlanningBoardComponent);
  const component = fixture.componentInstance;
  return { fixture, component, mockService };
}

describe('PlanningBoardComponent', () => {

  describe('initialization', () => {
    it('should load board on init', () => {
      const { fixture, mockService } = createComponent();
      fixture.detectChanges();
      expect(mockService.getPlanningBoard).toHaveBeenCalledTimes(1);
    });

    it('should render family card', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const card = fixture.nativeElement.querySelector('[data-testid="family-card-FAM-A"]');
      expect(card).toBeTruthy();
    });

    it('should expand family on toggle', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.toggleFamily('FAM-A');
      fixture.detectChanges();
      const table = fixture.nativeElement.querySelector('[data-testid="family-table-FAM-A"]');
      expect(table).toBeTruthy();
    });

    it('should show empty state when board is empty', () => {
      const { fixture, mockService } = createComponent();
      mockService.getPlanningBoard.mockReturnValue(of([]));
      fixture.detectChanges();
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-board"]');
      expect(empty).toBeTruthy();
    });

    it('should show error banner on load failure', () => {
      const { fixture, mockService } = createComponent();
      mockService.getPlanningBoard.mockReturnValue(throwError(() => new Error()));
      fixture.detectChanges();
      const banner = fixture.nativeElement.querySelector('[data-testid="error-banner"]');
      expect(banner).toBeTruthy();
    });
  });

  describe('planning status chips', () => {
    it('should render ALERT chip for product with netNeed > 0', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.toggleFamily('FAM-A');
      fixture.detectChanges();
      const alertChip = fixture.nativeElement.querySelector('[data-testid="status-chip-P001"]');
      expect(alertChip).toBeTruthy();
      expect(alertChip.classList.contains('planning-chip--alert')).toBe(true);
    });

    it('should render OK chip for product with netNeed = 0', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.toggleFamily('FAM-A');
      fixture.detectChanges();
      const okChip = fixture.nativeElement.querySelector('[data-testid="status-chip-P002"]');
      expect(okChip).toBeTruthy();
      expect(okChip.classList.contains('planning-chip--ok')).toBe(true);
    });
  });

  describe('MRP flow', () => {
    it('should show MRP button for SUPERVISOR', () => {
      const { fixture } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-execute-mrp"]');
      expect(btn).toBeTruthy();
    });

    it('should NOT show MRP button for OPERATOR', () => {
      const { fixture } = createComponent('OPERATOR');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-execute-mrp"]');
      expect(btn).toBeFalsy();
    });

    it('should show MRP modal after dryRun', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.executeMrp();
      fixture.detectChanges();
      const modal = fixture.nativeElement.querySelector('[data-testid="mrp-modal"]');
      expect(modal).toBeTruthy();
    });

    it('should NOT call runMrp on cancelMrp', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.showMrpModal.set(true);
      component.mrpDryRunResult.set(mockDryRunResult);
      component.cancelMrp();
      expect(mockService.runMrp).not.toHaveBeenCalled();
    });

    it('should call runMrp on confirmMrp', () => {
      const { fixture, component, mockService } = createComponent();
      fixture.detectChanges();
      component.showMrpModal.set(true);
      component.mrpDryRunResult.set(mockDryRunResult);
      component.confirmMrp();
      expect(mockService.runMrp).toHaveBeenCalledTimes(1);
    });

    it('should show toast after successful run', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.showMrpModal.set(true);
      component.mrpDryRunResult.set(mockDryRunResult);
      component.confirmMrp();
      fixture.detectChanges();
      const toast = fixture.nativeElement.querySelector('[data-testid="mrp-toast"]');
      expect(toast).toBeTruthy();
      expect(toast.textContent).toContain('sugestões geradas');
    });
  });

  describe('purchase needs', () => {
    it('should show no-mrp-run message when service errors', () => {
      const { fixture, mockService } = createComponent();
      mockService.getPurchaseNeeds.mockReturnValue(throwError(() => new Error()));
      fixture.detectChanges();
      const msg = fixture.nativeElement.querySelector('[data-testid="no-mrp-run"]');
      expect(msg).toBeTruthy();
    });
  });
});
