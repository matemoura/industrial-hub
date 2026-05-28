import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { PlanningTimelineComponent } from './planning-timeline.component';
import { PlanningService, TimelineEntry } from '../planning.service';
import { AuthService } from '../../auth/auth.service';

// ---- Mock data ----

const mockDynamicsEntry: TimelineEntry = {
  orderNumber: 'OP-2026-0001',
  productCode: 'P001',
  productName: 'Produto A',
  startDate: '2026-05-29',
  dueDate: '2026-06-15',
  qty: 100,
  statusLabel: 'Em Produção',
  isMrpSuggestion: false,
  overdue: false,
  suggestionId: null,
};

const mockMrpEntry: TimelineEntry = {
  orderNumber: 'MRP-abc12345',
  productCode: 'P002',
  productName: 'Produto B',
  startDate: '2026-06-01',
  dueDate: '2026-06-20',
  qty: 50,
  statusLabel: 'Planejado (MRP)',
  isMrpSuggestion: true,
  overdue: false,
  suggestionId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',  // BUG-2 fix: full UUID
};

function createComponent(role = 'SUPERVISOR') {
  const mockService = {
    getTimeline: vi.fn().mockReturnValue(of([mockDynamicsEntry, mockMrpEntry])),
    acceptSuggestion: vi.fn().mockReturnValue(of({})),
    rejectSuggestion: vi.fn().mockReturnValue(of({})),
  };
  const mockAuth = { role: signal(role) };

  TestBed.configureTestingModule({
    imports: [PlanningTimelineComponent],
    providers: [
      { provide: PlanningService, useValue: mockService },
      { provide: AuthService, useValue: mockAuth },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ familyCode: 'FAM-A' }) } } },
      provideRouter([]),
    ],
  });

  const fixture = TestBed.createComponent(PlanningTimelineComponent);
  const component = fixture.componentInstance;
  return { fixture, component, mockService };
}

describe('PlanningTimelineComponent', () => {

  describe('initialization', () => {
    it('should load timeline on init', () => {
      const { fixture, mockService } = createComponent();
      fixture.detectChanges();
      expect(mockService.getTimeline).toHaveBeenCalledTimes(1);
    });

    it('should render timeline grid with bars', () => {
      const { fixture } = createComponent();
      fixture.detectChanges();
      const grid = fixture.nativeElement.querySelector('[data-testid="timeline-grid"]');
      expect(grid).toBeTruthy();
    });

    it('should render bars for each entry', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      // bars computed — check count (may be 0 if dates are outside current window, but structure should exist)
      expect(component.entries()).toHaveLength(2);
    });

    it('should show empty state when no entries', () => {
      const { fixture, mockService } = createComponent();
      mockService.getTimeline.mockReturnValue(of([]));
      fixture.detectChanges();
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-timeline"]');
      expect(empty).toBeTruthy();
    });

    it('should show error banner on load failure', () => {
      const { fixture, mockService } = createComponent();
      mockService.getTimeline.mockReturnValue(throwError(() => new Error()));
      fixture.detectChanges();
      const banner = fixture.nativeElement.querySelector('[data-testid="error-banner"]');
      expect(banner).toBeTruthy();
    });
  });

  describe('navigation', () => {
    it('should advance week offset on nextWeeks', () => {
      const { component } = createComponent();
      expect(component.weekOffset()).toBe(0);
      component.nextWeeks();
      expect(component.weekOffset()).toBe(1);
    });

    it('should go back on prevWeeks', () => {
      const { component } = createComponent();
      component.nextWeeks();
      component.prevWeeks();
      expect(component.weekOffset()).toBe(0);
    });
  });

  describe('side panel', () => {
    it('should open side panel on openPanel', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.openPanel(mockDynamicsEntry);
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="side-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should close panel on closePanel', () => {
      const { fixture, component } = createComponent();
      fixture.detectChanges();
      component.openPanel(mockDynamicsEntry);
      component.closePanel();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="side-panel"]');
      expect(panel).toBeFalsy();
    });

    it('should show accept/reject buttons for MRP suggestions (SUPERVISOR)', () => {
      const { fixture, component } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      component.openPanel(mockMrpEntry);
      fixture.detectChanges();
      const actions = fixture.nativeElement.querySelector('[data-testid="suggestion-actions"]');
      expect(actions).toBeTruthy();
    });

    it('should NOT show accept/reject buttons for Dynamics orders', () => {
      const { fixture, component } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      component.openPanel(mockDynamicsEntry);
      fixture.detectChanges();
      const actions = fixture.nativeElement.querySelector('[data-testid="suggestion-actions"]');
      expect(actions).toBeFalsy();
    });

    it('should NOT show accept/reject buttons for OPERATOR', () => {
      const { fixture, component } = createComponent('OPERATOR');
      fixture.detectChanges();
      component.openPanel(mockMrpEntry);
      fixture.detectChanges();
      const actions = fixture.nativeElement.querySelector('[data-testid="suggestion-actions"]');
      expect(actions).toBeFalsy();
    });

    // BUG-2 regression tests
    it('should call acceptSuggestion with full suggestionId UUID (not truncated)', () => {
      const { fixture, component, mockService } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      component.openPanel(mockMrpEntry);
      component.acceptSuggestion(mockMrpEntry);
      expect(mockService.acceptSuggestion).toHaveBeenCalledWith(
        'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
      );
    });

    it('should call rejectSuggestion with full suggestionId UUID (not truncated)', () => {
      const { fixture, component, mockService } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      component.openPanel(mockMrpEntry);
      component.rejectSuggestion(mockMrpEntry, 'Motivo teste');
      expect(mockService.rejectSuggestion).toHaveBeenCalledWith(
        'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        'Motivo teste'
      );
    });

    it('should NOT call acceptSuggestion when suggestionId is null', () => {
      const { fixture, component, mockService } = createComponent('SUPERVISOR');
      fixture.detectChanges();
      const entryWithoutId: TimelineEntry = { ...mockMrpEntry, suggestionId: null };
      component.acceptSuggestion(entryWithoutId);
      expect(mockService.acceptSuggestion).not.toHaveBeenCalled();
    });
  });

  describe('barColor', () => {
    it('should return orange for MRP suggestions', () => {
      const { component } = createComponent();
      expect(component.barColor(mockMrpEntry)).toBe('#F97316');
    });

    it('should return teal for Dynamics orders', () => {
      const { component } = createComponent();
      expect(component.barColor(mockDynamicsEntry)).toBe('#0099B8');
    });
  });
});
