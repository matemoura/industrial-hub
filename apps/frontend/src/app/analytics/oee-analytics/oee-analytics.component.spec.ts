import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { OeeAnalyticsComponent } from './oee-analytics.component';
import { AnalyticsService } from '../analytics.service';

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

describe('OeeAnalyticsComponent', () => {
  let fixture: ComponentFixture<OeeAnalyticsComponent>;
  let component: OeeAnalyticsComponent;

  beforeEach(async () => {
    const service = makeService();

    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
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
    const service = { getOeeTrend: vi.fn().mockReturnValue(of(EMPTY_OEE_TREND)) };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(OeeAnalyticsComponent);
    f.detectChanges();

    const emptyState = f.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).toBeTruthy();
  });

  it('should show error message on API failure', async () => {
    const service = { getOeeTrend: vi.fn().mockReturnValue(throwError(() => new Error('fail'))) };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [OeeAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
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
    expect(service.getOeeTrend).toHaveBeenCalledWith(26);
  });

  it('should compute table rows correctly', () => {
    const rows = component.tableRows();
    expect(rows.length).toBe(3);
    expect(rows[0]).toEqual({ label: '2026-W01', oee: 72, samples: 5 });
  });
});
