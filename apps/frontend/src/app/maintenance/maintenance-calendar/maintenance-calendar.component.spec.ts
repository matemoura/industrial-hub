import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { MaintenanceCalendarComponent } from './maintenance-calendar.component';
import { MaintenanceService, ScheduleResponse } from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

const DAILY_SCHEDULE: ScheduleResponse = {
  id: 'sched-daily',
  equipmentId: 'eq-1',
  equipmentCode: 'EQ-001',
  equipmentName: 'Torno CNC',
  title: 'Lubrificação diária',
  description: null,
  priority: 'LOW',
  recurrence: 'DAILY',
  dayOfWeek: null,
  dayOfMonth: null,
  nextRunAt: '2026-05-01',
  lastRunAt: null,
  active: true,
  createdBy: 'supervisor1',
  createdAt: '2026-05-20T10:00:00',
};

const WEEKLY_SCHEDULE: ScheduleResponse = {
  ...DAILY_SCHEDULE,
  id: 'sched-weekly',
  title: 'Inspeção semanal',
  recurrence: 'WEEKLY',
  dayOfWeek: 5, // Friday
  nextRunAt: '2026-05-01',
};

const MONTHLY_SCHEDULE: ScheduleResponse = {
  ...DAILY_SCHEDULE,
  id: 'sched-monthly',
  title: 'Revisão mensal',
  recurrence: 'MONTHLY',
  dayOfMonth: 20,
  nextRunAt: '2026-05-01',
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

describe('MaintenanceCalendarComponent', () => {
  let fixture: ComponentFixture<MaintenanceCalendarComponent>;
  let component: MaintenanceCalendarComponent;
  let listSchedules: ReturnType<typeof vi.fn>;

  function setup(role = 'OPERATOR', schedules: ScheduleResponse[] = []) {
    listSchedules = vi.fn().mockReturnValue(of(schedules));
    const service = {
      listSchedules,
      deactivateSchedule: vi.fn().mockReturnValue(of(undefined)),
    };

    TestBed.configureTestingModule({
      imports: [MaintenanceCalendarComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MaintenanceService, useValue: service },
        { provide: AuthService, useValue: makeAuthService(role) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MaintenanceCalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('AC-5 — grade mensal', () => {
    it('should render calendar grid', () => {
      setup();
      const grid = fixture.nativeElement.querySelector('[data-testid="calendar-grid"]');
      expect(grid).toBeTruthy();
    });

    it('should render 42 day cells (6 weeks)', () => {
      setup();
      const days = fixture.nativeElement.querySelectorAll('[data-testid="calendar-day"]');
      expect(days.length).toBe(42);
    });

    it('should display current month label', () => {
      setup();
      const label = fixture.nativeElement.querySelector('[data-testid="month-label"]');
      expect(label.textContent.trim()).toBeTruthy();
    });
  });

  describe('AC-8 — navegação mensal', () => {
    it('should go to previous month on click', () => {
      setup();
      const initialMonth = component.currentMonth();
      fixture.nativeElement.querySelector('[data-testid="btn-prev-month"]').click();
      fixture.detectChanges();
      const expected = initialMonth === 0 ? 11 : initialMonth - 1;
      expect(component.currentMonth()).toBe(expected);
    });

    it('should go to next month on click', () => {
      setup();
      const initialMonth = component.currentMonth();
      fixture.nativeElement.querySelector('[data-testid="btn-next-month"]').click();
      fixture.detectChanges();
      const expected = initialMonth === 11 ? 0 : initialMonth + 1;
      expect(component.currentMonth()).toBe(expected);
    });

    it('should wrap year when going back from January', () => {
      setup();
      component.currentMonth.set(0);
      component.currentYear.set(2026);
      component.prevMonth();
      expect(component.currentMonth()).toBe(11);
      expect(component.currentYear()).toBe(2025);
    });

    it('should wrap year when going forward from December', () => {
      setup();
      component.currentMonth.set(11);
      component.currentYear.set(2026);
      component.nextMonth();
      expect(component.currentMonth()).toBe(0);
      expect(component.currentYear()).toBe(2027);
    });
  });

  describe('AC-6 — schedule badges', () => {
    it('should show badge for daily schedule', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      const badges = fixture.nativeElement.querySelectorAll('[data-testid="schedule-badge"]');
      expect(badges.length).toBeGreaterThan(0);
    });

    it('scheduleFallsOnDate — DAILY falls on any date >= nextRunAt', () => {
      setup();
      const future = new Date(2026, 5, 1); // June 1 local time
      expect(component.scheduleFallsOnDate(DAILY_SCHEDULE, future)).toBe(true);
    });

    it('scheduleFallsOnDate — DAILY does not fall before nextRunAt', () => {
      setup();
      const past = new Date(2026, 3, 30); // April 30 local time
      expect(component.scheduleFallsOnDate(DAILY_SCHEDULE, past)).toBe(false);
    });

    it('scheduleFallsOnDate — WEEKLY falls on correct day of week', () => {
      setup();
      // dayOfWeek = 5 (Friday)
      const friday = new Date(2026, 4, 22); // May 22 local time (Friday)
      expect(component.scheduleFallsOnDate(WEEKLY_SCHEDULE, friday)).toBe(true);
    });

    it('scheduleFallsOnDate — WEEKLY does not fall on wrong day', () => {
      setup();
      const thursday = new Date(2026, 4, 21); // May 21 local time (Thursday)
      expect(component.scheduleFallsOnDate(WEEKLY_SCHEDULE, thursday)).toBe(false);
    });

    it('scheduleFallsOnDate — MONTHLY falls on correct day of month', () => {
      setup();
      const may20 = new Date(2026, 4, 20); // May 20 local time
      expect(component.scheduleFallsOnDate(MONTHLY_SCHEDULE, may20)).toBe(true);
    });

    it('scheduleFallsOnDate — MONTHLY does not fall on wrong day', () => {
      setup();
      const may21 = new Date(2026, 4, 21); // May 21 local time
      expect(component.scheduleFallsOnDate(MONTHLY_SCHEDULE, may21)).toBe(false);
    });

    it('scheduleFallsOnDate — inactive schedule never falls', () => {
      setup();
      const inactive = { ...DAILY_SCHEDULE, active: false };
      expect(component.scheduleFallsOnDate(inactive, new Date(2026, 5, 1))).toBe(false);
    });
  });

  describe('SH-40 — DAILY schedule não aparece em dias fora do mês (padding)', () => {
    it('dia de padding anterior deve retornar array vazio', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      // Forçar o calendário para Junho 2026; o padding anterior inclui dias de Maio
      component.currentYear.set(2026);
      component.currentMonth.set(5); // Junho
      fixture.detectChanges();
      const weeks = component.calendarWeeks();
      // O primeiro dia do grid para Junho 2026 (1ª = segunda) começa em Junho — sem padding.
      // Usar Julho 2026 (1ª = quarta): padding inclui 29, 30 e 31 de Junho
      component.currentMonth.set(6); // Julho
      fixture.detectChanges();
      const weeksJuly = component.calendarWeeks();
      const paddingDayBefore = weeksJuly[0][0]; // 29 de junho (padding anterior)
      expect(paddingDayBefore.inCurrentMonth).toBe(false);
      expect(paddingDayBefore.schedules).toEqual([]);
    });

    it('dia de padding posterior deve retornar array vazio', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      // Maio 2026: 1ª = sexta. Grid começa segunda 27/04.
      // Padding posterior: 1 e 2 de Junho
      component.currentYear.set(2026);
      component.currentMonth.set(4); // Maio
      fixture.detectChanges();
      const weeks = component.calendarWeeks();
      const lastWeek = weeks[weeks.length - 1];
      const paddingDayAfter = lastWeek.find((d) => !d.inCurrentMonth && d.date.getMonth() === 5);
      expect(paddingDayAfter).toBeDefined();
      if (paddingDayAfter) {
        expect(paddingDayAfter.schedules).toEqual([]);
      }
    });

    it('dia do mês corrente deve exibir schedule DAILY', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      component.currentYear.set(2026);
      component.currentMonth.set(4); // Maio
      fixture.detectChanges();
      const weeks = component.calendarWeeks();
      const may15 = weeks.flat().find(
        (d) => d.inCurrentMonth && d.date.getDate() === 15 && d.date.getMonth() === 4,
      );
      expect(may15).toBeDefined();
      if (may15) {
        expect(may15.schedules.length).toBeGreaterThan(0);
      }
    });
  });

  describe('AC-7 — detail panel', () => {
    it('should open detail panel on badge click', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      const badge = fixture.nativeElement.querySelector('[data-testid="schedule-badge"]');
      badge?.click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="detail-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should close panel on close button click', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      component.selectSchedule(DAILY_SCHEDULE);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="btn-close-panel"]').click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="detail-panel"]');
      expect(panel).toBeFalsy();
    });

    it('OPERATOR should NOT see deactivate button', () => {
      setup('OPERATOR', [DAILY_SCHEDULE]);
      component.selectSchedule(DAILY_SCHEDULE);
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-deactivate"]');
      expect(btn).toBeFalsy();
    });

    it('SUPERVISOR should see deactivate button for active plan', () => {
      setup('SUPERVISOR', [DAILY_SCHEDULE]);
      component.selectSchedule(DAILY_SCHEDULE);
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="btn-deactivate"]');
      expect(btn).toBeTruthy();
    });
  });
});
