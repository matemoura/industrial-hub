import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { ScheduleListComponent } from './schedule-list.component';
import { MaintenanceService, ScheduleResponse } from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

const SCHEDULE: ScheduleResponse = {
  id: 'sched-1',
  equipmentId: 'eq-1',
  equipmentCode: 'EQ-001',
  equipmentName: 'Torno CNC',
  title: 'Lubrificação semanal',
  description: null,
  priority: 'MEDIUM',
  recurrence: 'WEEKLY',
  dayOfWeek: 5,
  dayOfMonth: null,
  nextRunAt: '2026-05-22',
  lastRunAt: null,
  active: true,
  createdBy: 'supervisor1',
  createdAt: '2026-05-20T10:00:00',
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

describe('ScheduleListComponent', () => {
  let fixture: ComponentFixture<ScheduleListComponent>;
  let component: ScheduleListComponent;
  let listSchedules: ReturnType<typeof vi.fn>;

  function setup(role = 'OPERATOR', schedules: ScheduleResponse[] = [SCHEDULE]) {
    listSchedules = vi.fn().mockReturnValue(of(schedules));
    const service = { listSchedules };

    TestBed.configureTestingModule({
      imports: [ScheduleListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MaintenanceService, useValue: service },
        { provide: AuthService, useValue: makeAuthService(role) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScheduleListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('AC-1 — tabela com planos ativos', () => {
    it('should display schedule table', () => {
      setup();
      const table = fixture.nativeElement.querySelector('[data-testid="schedule-table"]');
      expect(table).toBeTruthy();
    });

    it('should render a row per schedule', () => {
      setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="schedule-row"]');
      expect(rows.length).toBe(1);
    });

    it('should call listSchedules on init', () => {
      setup();
      expect(listSchedules).toHaveBeenCalled();
    });

    it('should show recurrence detail for weekly', () => {
      setup();
      const detail = component.recurrenceDetail(SCHEDULE);
      expect(detail).toContain('Semanal');
      expect(detail).toContain('Sex');
    });

    it('should show recurrence detail for monthly', () => {
      setup('OPERATOR', [{ ...SCHEDULE, recurrence: 'MONTHLY', dayOfWeek: null, dayOfMonth: 15 }]);
      const detail = component.recurrenceDetail({ ...SCHEDULE, recurrence: 'MONTHLY', dayOfWeek: null, dayOfMonth: 15 });
      expect(detail).toContain('Mensal');
      expect(detail).toContain('15');
    });

    it('should show recurrence detail for daily', () => {
      setup();
      const detail = component.recurrenceDetail({ ...SCHEDULE, recurrence: 'DAILY' });
      expect(detail).toBe('Diária');
    });
  });

  describe('AC-2 — role-based visibility', () => {
    it('OPERATOR should NOT see new schedule button', () => {
      setup('OPERATOR');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-schedule"]');
      expect(btn).toBeFalsy();
    });

    it('SUPERVISOR should see new schedule button', () => {
      setup('SUPERVISOR');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-schedule"]');
      expect(btn).toBeTruthy();
    });

    it('ADMIN should see new schedule button', () => {
      setup('ADMIN');
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-schedule"]');
      expect(btn).toBeTruthy();
    });
  });

  describe('empty state', () => {
    it('should show empty state when no schedules', () => {
      setup('OPERATOR', []);
      const empty = fixture.nativeElement.querySelector('[data-testid="empty"]');
      expect(empty).toBeTruthy();
    });
  });

  describe('error state', () => {
    it('should show error banner on load failure', () => {
      const failingService = { listSchedules: vi.fn().mockReturnValue(throwError(() => new Error('fail'))) };
      TestBed.configureTestingModule({
        imports: [ScheduleListComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: MaintenanceService, useValue: failingService },
          { provide: AuthService, useValue: makeAuthService('OPERATOR') },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(ScheduleListComponent);
      fixture.detectChanges();
      const err = fixture.nativeElement.querySelector('[data-testid="error"]');
      expect(err).toBeTruthy();
    });
  });
});
