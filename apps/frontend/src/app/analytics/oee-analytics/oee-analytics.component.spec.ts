import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { OeeAnalyticsComponent } from './oee-analytics.component';
import { AnalyticsService } from '../analytics.service';
import { AdminService, Shift } from '../../admin/admin.service';

const MOCK_SHIFTS: Shift[] = [
  { id: 's1', name: 'Manhã', startTime: '06:00', endTime: '14:00', overnight: false, active: true },
  { id: 's2', name: 'Tarde', startTime: '14:00', endTime: '22:00', overnight: false, active: true },
];

const makeAdminService = (shifts: Shift[] = []) => ({
  getShifts: vi.fn().mockReturnValue(of(shifts)),
});

const MOCK_OEE_TREND = {
  weekLabels: ['2026-W01', '2026-W02', '2026-W03'],
  oeeValues: [0.72, 0.68, 0.75],
  sampleCounts: [5, 4, 6],
};

const EMPTY_OEE_TREND = {
  weekLabels: [],
  oeeValues: [],
  sampleCounts: [],
};

function makeService(data = MOCK_OEE_TREND) {
  return {
    getOeeTrend: vi.fn().mockReturnValue(of(data)),
  };
}

async function resetWithServices(
  analyticsData = MOCK_OEE_TREND,
  shifts: Shift[] = [],
): Promise<ComponentFixture<OeeAnalyticsComponent>> {
  await TestBed.resetTestingModule();
  const service = makeService(analyticsData);
  const adminService = makeAdminService(shifts);

  await TestBed.configureTestingModule({
    imports: [OeeAnalyticsComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AnalyticsService, useValue: service },
      { provide: AdminService, useValue: adminService },
    ],
  }).compileComponents();

  const f = TestBed.createComponent(OeeAnalyticsComponent);
  f.detectChanges();
  return f;
}

describe('OeeAnalyticsComponent', () => {
  let fixture: ComponentFixture<OeeAnalyticsComponent>;
  let component: OeeAnalyticsComponent;

  beforeEach(async () => {
    const service = makeService();
    const adminService = makeAdminService();

    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
        { provide: AdminService, useValue: adminService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OeeAnalyticsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render period dropdown with default 12 weeks', () => {
    const select = fixture.nativeElement.querySelector('[data-testid="period-select"]');
    expect(select).toBeTruthy();
    expect(component.selectedWeeks()).toBe(12);
  });

  it('should render print button', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="print-btn"]');
    expect(btn).toBeTruthy();
  });

  it('should display chart area when data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="chart-area"]');
    expect(chart).toBeTruthy();
  });

  it('should display data table with correct rows', () => {
    const table = fixture.nativeElement.querySelector('[data-testid="data-table"]');
    expect(table).toBeTruthy();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(3);
  });

  it('should compute OEE values as percentages', () => {
    expect(component.chartValues()).toEqual([72, 68, 75]);
  });

  it('should not show empty state when data is available', () => {
    const emptyState = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).toBeFalsy();
  });

  it('should show empty state when no data', async () => {
    const f = await resetWithServices(EMPTY_OEE_TREND);
    const emptyState = f.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).toBeTruthy();
  });

  it('should show error message on API failure', async () => {
    await TestBed.resetTestingModule();
    const service = { getOeeTrend: vi.fn().mockReturnValue(throwError(() => new Error('fail'))) };
    const adminService = makeAdminService();

    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
        { provide: AdminService, useValue: adminService },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(OeeAnalyticsComponent);
    f.detectChanges();

    const errEl = f.nativeElement.querySelector('[data-testid="error-msg"]');
    expect(errEl).toBeTruthy();
    expect(errEl.textContent).toContain('Erro ao carregar dados de OEE');
  });

  it('should call getOeeTrend with selected weeks on period change', () => {
    const service = TestBed.inject(AnalyticsService) as any;
    const select = fixture.nativeElement.querySelector('[data-testid="period-select"]');
    select.value = '26';
    select.dispatchEvent(new Event('change'));
    expect(service.getOeeTrend).toHaveBeenCalledWith(26, false, undefined);
  });

  it('should compute table rows correctly', () => {
    const rows = component.tableRows();
    expect(rows.length).toBe(3);
    expect(rows[0]).toEqual({ label: '2026-W01', oee: 72, samples: 5 });
  });

  it('should render toggle-exclude-downtime checkbox', () => {
    const toggle = fixture.nativeElement.querySelector('[data-testid="toggle-exclude-downtime"]');
    expect(toggle).toBeTruthy();
    expect(toggle.type).toBe('checkbox');
  });

  it('should reload data when toggle-exclude-downtime is changed', () => {
    const service = TestBed.inject(AnalyticsService) as any;
    const callsBefore = service.getOeeTrend.mock.calls.length;
    const toggle = fixture.nativeElement.querySelector('[data-testid="toggle-exclude-downtime"]');
    toggle.dispatchEvent(new Event('change'));
    expect(service.getOeeTrend.mock.calls.length).toBe(callsBefore + 1);
  });

  // ─── SEC-083 — loadShifts() falha → errorMsg preenchida ──────────────────
  it('(SEC-083-a) quando getShifts() falha → errorMsg() contém mensagem de erro', async () => {
    await TestBed.resetTestingModule();
    const failingAdminService = {
      getShifts: vi.fn().mockReturnValue(throwError(() => new Error('network error'))),
    };
    const analyticsService = makeService();

    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: analyticsService },
        { provide: AdminService, useValue: failingAdminService },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(OeeAnalyticsComponent);
    f.detectChanges();

    expect(f.componentInstance.shiftsErrorMsg()).toBe('Erro ao carregar turnos.');
  });

  it('(SEC-083-b) quando getShifts() falha → console.error não é chamado', async () => {
    await TestBed.resetTestingModule();
    const consoleSpy = vi.spyOn(console, 'error');
    const failingAdminService = {
      getShifts: vi.fn().mockReturnValue(throwError(() => new Error('network error'))),
    };
    const analyticsService = makeService();

    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: analyticsService },
        { provide: AdminService, useValue: failingAdminService },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(OeeAnalyticsComponent);
    f.detectChanges();

    expect(consoleSpy).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  // ─── US-056 (a) dropdown exibido quando shifts() tem 2 itens ──────────────
  it('(US-056-a) deve exibir dropdown de turno quando shifts() tem 2 itens', async () => {
    const f = await resetWithServices(MOCK_OEE_TREND, MOCK_SHIFTS);
    f.componentInstance.shifts.set(MOCK_SHIFTS);
    f.detectChanges();

    const select = f.nativeElement.querySelector('[data-testid="shift-select"]');
    expect(select).toBeTruthy();
  });

  // ─── US-056 (b) dropdown oculto quando shifts() vazio ─────────────────────
  it('(US-056-b) deve ocultar dropdown de turno quando shifts() está vazio', async () => {
    const f = await resetWithServices(MOCK_OEE_TREND, []);
    f.detectChanges();

    const select = f.nativeElement.querySelector('[data-testid="shift-select"]');
    expect(select).toBeFalsy();
  });

  // ─── US-056 (c) seleção chama serviço com shiftId correto ─────────────────
  it('(US-056-c) seleção de turno deve chamar getOeeTrend com shiftId correto', async () => {
    const f = await resetWithServices(MOCK_OEE_TREND, MOCK_SHIFTS);
    f.componentInstance.shifts.set(MOCK_SHIFTS);
    f.detectChanges();

    const service = TestBed.inject(AnalyticsService) as any;
    const select = f.nativeElement.querySelector('[data-testid="shift-select"]');
    select.value = 's1';
    select.dispatchEvent(new Event('change'));

    const calls = service.getOeeTrend.mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[2]).toBe('s1');
  });
});
