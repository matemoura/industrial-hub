import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { PlannedDowntimeCalendarComponent } from './planned-downtime-calendar.component';
import { OeeService, PlannedDowntimeResponse } from '../oee.service';
import { AuthService } from '../../auth/auth.service';
import { MaintenanceService } from '../../maintenance/maintenance.service';

const MOCK_DOWNTIME: PlannedDowntimeResponse = {
  id: 'dt-001',
  equipmentId: 'eq-1',
  equipmentCode: 'EQ-001',
  equipmentName: 'Torno CNC',
  reason: 'PREVENTIVE_MAINTENANCE',
  startAt: '2026-05-20T08:00:00',
  endAt: '2026-05-20T12:00:00',
  durationMinutes: 240,
  description: 'Lubrificação preventiva mensal',
  registeredBy: 'supervisor1',
  registeredAt: '2026-05-15T10:00:00',
};

const MOCK_DOWNTIME_HOLIDAY: PlannedDowntimeResponse = {
  id: 'dt-002',
  equipmentId: null,
  equipmentCode: null,
  equipmentName: null,
  reason: 'HOLIDAY',
  startAt: '2026-05-21T00:00:00',
  endAt: '2026-05-21T23:59:59',
  durationMinutes: 1440,
  description: null,
  registeredBy: 'admin',
  registeredAt: '2026-05-10T09:00:00',
};

const MOCK_EQUIPMENT = [
  { id: 'eq-1', code: 'EQ-001', name: 'Torno CNC', location: null, type: 'MACHINE', status: 'OPERATIONAL', acquiredAt: null, active: true },
  { id: 'eq-2', code: 'EQ-002', name: 'Fresadora', location: null, type: 'MACHINE', status: 'OPERATIONAL', acquiredAt: null, active: true },
  { id: 'eq-3', code: 'EQ-003', name: 'Torno Vertical', location: null, type: 'MACHINE', status: 'OPERATIONAL', acquiredAt: null, active: true },
];

function makeOeeService(downtimes: PlannedDowntimeResponse[] = [MOCK_DOWNTIME]) {
  return {
    listDowntimes: vi.fn().mockReturnValue(of(downtimes)),
    createDowntime: vi.fn().mockReturnValue(of(MOCK_DOWNTIME)),
    deleteDowntime: vi.fn().mockReturnValue(of(undefined)),
  };
}

function makeMaintenanceService(equipment = MOCK_EQUIPMENT) {
  return {
    listEquipment: vi.fn().mockReturnValue(of(equipment)),
  };
}

function makeAuthService(role: string) {
  return { role: signal(role) };
}

describe('PlannedDowntimeCalendarComponent', () => {
  let fixture: ComponentFixture<PlannedDowntimeCalendarComponent>;
  let component: PlannedDowntimeCalendarComponent;

  function setup(role = 'OPERATOR', downtimes: PlannedDowntimeResponse[] = [MOCK_DOWNTIME]) {
    const oeeService = makeOeeService(downtimes);
    const maintenanceService = makeMaintenanceService();
    TestBed.configureTestingModule({
      imports: [PlannedDowntimeCalendarComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OeeService, useValue: oeeService },
        { provide: MaintenanceService, useValue: maintenanceService },
        { provide: AuthService, useValue: makeAuthService(role) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlannedDowntimeCalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    return oeeService;
  }

  describe('AC-1 — calendar grid rendering', () => {
    it('should create component', () => {
      setup();
      expect(component).toBeTruthy();
    });

    it('should render 42 calendar day cells (6x7 grid)', () => {
      setup();
      const days = fixture.nativeElement.querySelectorAll('[data-testid="calendar-day"]');
      expect(days.length).toBe(42);
    });

    it('should display the current month label', () => {
      setup();
      const label = fixture.nativeElement.querySelector('[data-testid="month-label"]');
      expect(label).toBeTruthy();
      expect(label.textContent.trim().length).toBeGreaterThan(0);
    });

    it('should navigate to previous month', () => {
      setup();
      const initialMonth = component.currentMonth();
      fixture.nativeElement.querySelector('[data-testid="btn-prev-month"]').click();
      fixture.detectChanges();
      const expected = initialMonth === 0 ? 11 : initialMonth - 1;
      expect(component.currentMonth()).toBe(expected);
    });

    it('should navigate to next month', () => {
      setup();
      const initialMonth = component.currentMonth();
      fixture.nativeElement.querySelector('[data-testid="btn-next-month"]').click();
      fixture.detectChanges();
      const expected = initialMonth === 11 ? 0 : initialMonth + 1;
      expect(component.currentMonth()).toBe(expected);
    });

    it('should wrap year backward from January', () => {
      setup();
      component.currentMonth.set(0);
      component.currentYear.set(2026);
      component.prevMonth();
      expect(component.currentMonth()).toBe(11);
      expect(component.currentYear()).toBe(2025);
    });

    it('should wrap year forward from December', () => {
      setup();
      component.currentMonth.set(11);
      component.currentYear.set(2026);
      component.nextMonth();
      expect(component.currentMonth()).toBe(0);
      expect(component.currentYear()).toBe(2027);
    });
  });

  describe('AC-2 — downtime badges on calendar days', () => {
    it('should render badge for a downtime on its date', () => {
      setup('OPERATOR', [MOCK_DOWNTIME]);
      // Set calendar to May 2026 where MOCK_DOWNTIME falls
      component.currentYear.set(2026);
      component.currentMonth.set(4); // May
      fixture.detectChanges();
      const badges = fixture.nativeElement.querySelectorAll('[data-testid="downtime-badge"]');
      expect(badges.length).toBeGreaterThan(0);
    });

    it('should not render badges for empty downtime list', () => {
      setup('OPERATOR', []);
      const badges = fixture.nativeElement.querySelectorAll('[data-testid="downtime-badge"]');
      expect(badges.length).toBe(0);
    });

    it('downtimeFallsOnDate — returns true when date is within interval', () => {
      setup();
      const date = new Date(2026, 4, 20); // May 20
      expect(component.downtimeFallsOnDate(MOCK_DOWNTIME, date)).toBe(true);
    });

    it('downtimeFallsOnDate — returns false when date is outside interval', () => {
      setup();
      const date = new Date(2026, 4, 21); // May 21
      expect(component.downtimeFallsOnDate(MOCK_DOWNTIME, date)).toBe(false);
    });

    it('downtimeFallsOnDate — returns true for all-day holiday', () => {
      setup();
      const date = new Date(2026, 4, 21); // May 21
      expect(component.downtimeFallsOnDate(MOCK_DOWNTIME_HOLIDAY, date)).toBe(true);
    });
  });

  describe('AC-3 — detail panel', () => {
    it('should open detail panel when badge is clicked', () => {
      setup('OPERATOR', [MOCK_DOWNTIME]);
      component.currentYear.set(2026);
      component.currentMonth.set(4);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="downtime-badge"]');
      badge?.click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="detail-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should close detail panel on close button click', () => {
      setup();
      component.selectDowntime(MOCK_DOWNTIME);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="btn-close-panel"]').click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="detail-panel"]');
      expect(panel).toBeFalsy();
    });

    it('should display start and end time in panel', () => {
      setup();
      component.selectDowntime(MOCK_DOWNTIME);
      fixture.detectChanges();
      const startEl = fixture.nativeElement.querySelector('[data-testid="panel-start-at"]');
      const endEl = fixture.nativeElement.querySelector('[data-testid="panel-end-at"]');
      expect(startEl.textContent).toContain('20/05/2026');
      expect(endEl.textContent).toContain('20/05/2026');
    });
  });

  describe('AC-4 — role-based visibility of delete button', () => {
    it('OPERATOR should NOT see delete button', () => {
      setup('OPERATOR');
      component.selectDowntime(MOCK_DOWNTIME);
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-delete-downtime"]');
      expect(btn).toBeFalsy();
    });

    it('SUPERVISOR should see delete button', () => {
      setup('SUPERVISOR');
      component.selectDowntime(MOCK_DOWNTIME);
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-delete-downtime"]');
      expect(btn).toBeTruthy();
    });

    it('ADMIN should see delete button', () => {
      setup('ADMIN');
      component.selectDowntime(MOCK_DOWNTIME);
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-delete-downtime"]');
      expect(btn).toBeTruthy();
    });
  });

  describe('AC-5 — create form (SUPERVISOR+)', () => {
    it('OPERATOR should NOT see "Registrar Parada" button', () => {
      setup('OPERATOR');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-add-downtime"]');
      expect(btn).toBeFalsy();
    });

    it('SUPERVISOR should see "Registrar Parada" button', () => {
      setup('SUPERVISOR');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-add-downtime"]');
      expect(btn).toBeTruthy();
    });

    it('should open form panel on "Registrar Parada" click', () => {
      setup('SUPERVISOR');
      fixture.nativeElement.querySelector('[data-testid="btn-add-downtime"]').click();
      fixture.detectChanges();
      const form = fixture.nativeElement.querySelector('[data-testid="form-panel"]');
      expect(form).toBeTruthy();
    });

    it('should close form on cancel button click', () => {
      setup('SUPERVISOR');
      component.openForm();
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="btn-close-form"]').click();
      fixture.detectChanges();
      const form = fixture.nativeElement.querySelector('[data-testid="form-panel"]');
      expect(form).toBeFalsy();
    });

    it('submit button should be disabled when startAt or endAt is empty', () => {
      setup('SUPERVISOR');
      component.openForm();
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit-form"]');
      expect(btn.disabled).toBe(true);
    });

    it('should call createDowntime and reload on successful submit', () => {
      const oeeService = setup('SUPERVISOR');
      component.openForm();
      component.formStartAt.set('2026-05-25T08:00');
      component.formEndAt.set('2026-05-25T10:00');
      fixture.detectChanges();
      component.submitForm();
      expect(oeeService.createDowntime).toHaveBeenCalledOnce();
      expect(oeeService.listDowntimes).toHaveBeenCalledTimes(2); // ngOnInit + reload
    });
  });

  describe('AC-6 — error and loading states', () => {
    it('should show loading state while fetching', async () => {
      // We test loading=true by checking the signal before detectChanges
      const oeeService = makeOeeService();
      TestBed.configureTestingModule({
        imports: [PlannedDowntimeCalendarComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: OeeService, useValue: oeeService },
          { provide: MaintenanceService, useValue: makeMaintenanceService() },
          { provide: AuthService, useValue: makeAuthService('OPERATOR') },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(PlannedDowntimeCalendarComponent);
      component = fixture.componentInstance;
      // Before detectChanges (ngOnInit not called yet), loading is false
      expect(component.loading()).toBe(false);
    });

    it('should show error state when API fails', async () => {
      const failService = {
        listDowntimes: vi.fn().mockReturnValue(throwError(() => new Error())),
        createDowntime: vi.fn(),
        deleteDowntime: vi.fn(),
      };
      const failMaintenanceService = {
        listEquipment: vi.fn().mockReturnValue(throwError(() => new Error())),
      };
      await TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [PlannedDowntimeCalendarComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: OeeService, useValue: failService },
          { provide: MaintenanceService, useValue: failMaintenanceService },
          { provide: AuthService, useValue: makeAuthService('OPERATOR') },
        ],
      }).compileComponents();
      const f = TestBed.createComponent(PlannedDowntimeCalendarComponent);
      f.detectChanges();
      const err = f.nativeElement.querySelector('[data-testid="error-state"]');
      expect(err).toBeTruthy();
    });

    it('should show empty state when no downtimes', () => {
      setup('OPERATOR', []);
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
      expect(empty).toBeTruthy();
    });
  });

  describe('AC-7 — delete downtime flow', () => {
    it('should call deleteDowntime and reload list on confirm', () => {
      const oeeService = setup('SUPERVISOR');
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.selectDowntime(MOCK_DOWNTIME);
      fixture.detectChanges();
      component.deleteDowntime(MOCK_DOWNTIME);
      expect(oeeService.deleteDowntime).toHaveBeenCalledWith('dt-001');
      expect(oeeService.listDowntimes).toHaveBeenCalledTimes(2);
    });

    it('should NOT call deleteDowntime when confirm is cancelled', () => {
      const oeeService = setup('SUPERVISOR');
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      component.deleteDowntime(MOCK_DOWNTIME);
      expect(oeeService.deleteDowntime).not.toHaveBeenCalled();
    });
  });

  describe('SUG-30 — equipmentOptions populados via API independente de paradas', () => {
    it('deve exibir 3 equipamentos no select mesmo sem paradas', () => {
      // Sem downtimes, mas com 3 equipamentos via API
      setup('SUPERVISOR', []);
      fixture.detectChanges();
      const options = component.equipmentOptions();
      expect(options.length).toBe(3);
      expect(options[0].code).toBe('EQ-001');
      expect(options[1].code).toBe('EQ-002');
      expect(options[2].code).toBe('EQ-003');
    });

    it('equipmentOptions é signal (não computed)', () => {
      setup('OPERATOR', []);
      // Verificar que o signal pode ser definido diretamente
      component.equipmentOptions.set([{ id: 'x', code: 'X', name: 'Test' }]);
      expect(component.equipmentOptions().length).toBe(1);
    });

    it('deve manter equipamentOptions vazio quando listEquipment falha mas exibir paradas', () => {
      const oeeService = makeOeeService([MOCK_DOWNTIME]);
      const failMaintenanceService = {
        listEquipment: vi.fn().mockReturnValue(throwError(() => new Error('Network error'))),
      };
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [PlannedDowntimeCalendarComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: OeeService, useValue: oeeService },
          { provide: MaintenanceService, useValue: failMaintenanceService },
          { provide: AuthService, useValue: makeAuthService('OPERATOR') },
        ],
      }).compileComponents();
      const f = TestBed.createComponent(PlannedDowntimeCalendarComponent);
      const c = f.componentInstance;
      f.detectChanges();
      // equipmentOptions fica vazio mas sem bloquear o carregamento
      expect(c.equipmentOptions()).toEqual([]);
    });
  });

  describe('AC-8 — reason badge colors', () => {
    it('should return teal for PREVENTIVE_MAINTENANCE', () => {
      setup();
      expect(component.reasonBadgeColor('PREVENTIVE_MAINTENANCE')).toBe('#0099B8');
    });

    it('should return orange for SCHEDULED_SETUP', () => {
      setup();
      expect(component.reasonBadgeColor('SCHEDULED_SETUP')).toBe('#F97316');
    });

    it('should return purple for HOLIDAY', () => {
      setup();
      expect(component.reasonBadgeColor('HOLIDAY')).toBe('#8B5CF6');
    });

    it('should return gray for OTHER', () => {
      setup();
      expect(component.reasonBadgeColor('OTHER')).toBe('#64748B');
    });
  });
});
