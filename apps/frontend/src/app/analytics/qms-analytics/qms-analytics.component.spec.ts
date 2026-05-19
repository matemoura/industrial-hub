import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { QmsAnalyticsComponent } from './qms-analytics.component';
import { AnalyticsService } from '../analytics.service';

const MOCK_PARETO = {
  byType: { PROCESS: 12, PRODUCT: 8, SUPPLIER: 3 },
  bySeverity: { CRITICAL: 3, HIGH: 10, MEDIUM: 6, LOW: 4 },
};

const MOCK_TREND = {
  labels: ['2026-W01', '2026-W02'],
  values: [5, 8],
};

function makeService(pareto = MOCK_PARETO, trend = MOCK_TREND) {
  return {
    getNcPareto: vi.fn().mockReturnValue(of(pareto)),
    getNcTrend: vi.fn().mockReturnValue(of(trend)),
  };
}

describe('QmsAnalyticsComponent', () => {
  let fixture: ComponentFixture<QmsAnalyticsComponent>;
  let component: QmsAnalyticsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QmsAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: makeService() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QmsAnalyticsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render days period dropdown', () => {
    const select = fixture.nativeElement.querySelector('[data-testid="days-select"]');
    expect(select).toBeTruthy();
    expect(component.selectedDays()).toBe(90);
  });

  it('should render weeks period dropdown', () => {
    const select = fixture.nativeElement.querySelector('[data-testid="weeks-select"]');
    expect(select).toBeTruthy();
    expect(component.selectedWeeks()).toBe(12);
  });

  it('should show type chart when pareto data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="type-chart"]');
    expect(chart).toBeTruthy();
  });

  it('should show severity chart when pareto data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="severity-chart"]');
    expect(chart).toBeTruthy();
  });

  it('should show trend chart when trend data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="trend-chart"]');
    expect(chart).toBeTruthy();
  });

  it('should compute typeLabels from pareto data', () => {
    expect(component.typeLabels()).toEqual(['PROCESS', 'PRODUCT', 'SUPPLIER']);
  });

  it('should compute typeValues from pareto data', () => {
    expect(component.typeValues()).toEqual([12, 8, 3]);
  });

  it('should map severity colors correctly', () => {
    const colors = component.severityColors();
    expect(colors[0]).toBe('#E53E3E'); // CRITICAL
    expect(colors[1]).toBe('#DD6B20'); // HIGH
  });

  it('should reload pareto when days selection changes', () => {
    const service = TestBed.inject(AnalyticsService) as any;
    const select = fixture.nativeElement.querySelector('[data-testid="days-select"]');
    select.value = '30';
    select.dispatchEvent(new Event('change'));
    expect(service.getNcPareto).toHaveBeenCalledWith(30);
  });

  it('should reload trend when weeks selection changes', () => {
    const service = TestBed.inject(AnalyticsService) as any;
    const select = fixture.nativeElement.querySelector('[data-testid="weeks-select"]');
    select.value = '4';
    select.dispatchEvent(new Event('change'));
    expect(service.getNcTrend).toHaveBeenCalledWith(4);
  });

  it('should show empty pareto state when no data', async () => {
    const emptyPareto = { byType: {}, bySeverity: {} };
    const service = {
      getNcPareto: vi.fn().mockReturnValue(of(emptyPareto)),
      getNcTrend: vi.fn().mockReturnValue(of({ labels: [], values: [] })),
    };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [QmsAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(QmsAnalyticsComponent);
    f.detectChanges();

    expect(f.nativeElement.querySelector('[data-testid="empty-pareto"]')).toBeTruthy();
    expect(f.nativeElement.querySelector('[data-testid="empty-trend"]')).toBeTruthy();
  });

  it('should show error messages on API failure', async () => {
    const service = {
      getNcPareto: vi.fn().mockReturnValue(throwError(() => new Error())),
      getNcTrend: vi.fn().mockReturnValue(throwError(() => new Error())),
    };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [QmsAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(QmsAnalyticsComponent);
    f.detectChanges();

    const errors = f.nativeElement.querySelectorAll('.error-msg');
    expect(errors.length).toBe(2);
  });
});
