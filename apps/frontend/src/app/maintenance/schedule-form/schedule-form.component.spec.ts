import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { ScheduleFormComponent } from './schedule-form.component';
import { MaintenanceService } from '../maintenance.service';

const MOCK_EQUIPMENT = [
  { id: 'eq-1', code: 'EQ-001', name: 'Torno CNC', type: 'MACHINE', status: 'OPERATIONAL', location: null, acquiredAt: null, active: true },
];

function makeRoute(id?: string) {
  return { snapshot: { paramMap: { get: (_: string) => id ?? null } } };
}

describe('ScheduleFormComponent — Create', () => {
  let fixture: ComponentFixture<ScheduleFormComponent>;
  let component: ScheduleFormComponent;

  beforeEach(async () => {
    const service = {
      listEquipment: vi.fn().mockReturnValue(of(MOCK_EQUIPMENT)),
      getSchedule: vi.fn(),
      createSchedule: vi.fn(),
      updateSchedule: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ScheduleFormComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MaintenanceService, useValue: service },
        { provide: ActivatedRoute, useValue: makeRoute() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScheduleFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render form in create mode', () => {
    const title = fixture.nativeElement.querySelector('h1');
    expect(title.textContent).toContain('Novo Plano');
  });

  it('should show equipment select in create mode', () => {
    const sel = fixture.nativeElement.querySelector('[data-testid="equipment-select"]');
    expect(sel).toBeTruthy();
  });

  it('submit button should be disabled when form is empty', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]');
    expect(btn.disabled).toBe(true);
  });

  it('should show dayOfWeek select when WEEKLY is selected', () => {
    component.recurrence.set('WEEKLY');
    fixture.detectChanges();
    const sel = fixture.nativeElement.querySelector('[data-testid="day-of-week-select"]');
    expect(sel).toBeTruthy();
  });

  it('should NOT show dayOfWeek select for DAILY', () => {
    component.recurrence.set('DAILY');
    fixture.detectChanges();
    const sel = fixture.nativeElement.querySelector('[data-testid="day-of-week-select"]');
    expect(sel).toBeFalsy();
  });

  it('should show dayOfMonth input when MONTHLY is selected', () => {
    component.recurrence.set('MONTHLY');
    fixture.detectChanges();
    const inp = fixture.nativeElement.querySelector('[data-testid="day-of-month-input"]');
    expect(inp).toBeTruthy();
  });

  it('should NOT show dayOfMonth input for WEEKLY', () => {
    component.recurrence.set('WEEKLY');
    fixture.detectChanges();
    const inp = fixture.nativeElement.querySelector('[data-testid="day-of-month-input"]');
    expect(inp).toBeFalsy();
  });

  it('should clear dayOfWeek when recurrence changes', () => {
    component.dayOfWeek.set(5);
    component.onRecurrenceChange('DAILY');
    expect(component.dayOfWeek()).toBe('');
  });

  it('isValid should be true when all required fields set for DAILY', () => {
    component.equipmentId.set('eq-1');
    component.title.set('Lubrificação');
    component.priority.set('MEDIUM');
    component.recurrence.set('DAILY');
    expect(component.isValid).toBe(true);
  });

  it('isValid should be false for WEEKLY without dayOfWeek', () => {
    component.equipmentId.set('eq-1');
    component.title.set('Test');
    component.priority.set('LOW');
    component.recurrence.set('WEEKLY');
    expect(component.isValid).toBe(false);
  });

  it('should navigate to /maintenance/schedules after successful create', () => {
    const service = TestBed.inject(MaintenanceService) as any;
    service.createSchedule.mockReturnValue(of({ id: 'sched-1' }));

    component.equipmentId.set('eq-1');
    component.title.set('Lubrificação');
    component.priority.set('MEDIUM');
    component.recurrence.set('DAILY');

    const navigateSpy = vi.fn();
    (component as any).router = { navigate: navigateSpy };

    component.submit();

    expect(service.createSchedule).toHaveBeenCalledWith(
      expect.objectContaining({
        equipmentId: 'eq-1',
        title: 'Lubrificação',
        priority: 'MEDIUM',
        recurrence: 'DAILY',
      }),
    );
    expect(navigateSpy).toHaveBeenCalledWith(
      ['/maintenance/schedules'],
      expect.objectContaining({ state: expect.objectContaining({ toast: expect.any(String) }) }),
    );
  });
});
