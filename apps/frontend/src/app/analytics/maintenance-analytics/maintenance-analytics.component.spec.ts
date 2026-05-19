import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { MaintenanceAnalyticsComponent } from './maintenance-analytics.component';
import { AnalyticsService } from '../analytics.service';

const MOCK_MTTR = {
  monthLabels: ['2026-01', '2026-02', '2026-03'],
  mttrValues: [4.5, 3.2, 5.1],
};

const MOCK_WO = {
  byStatus: { OPEN: 10, IN_PROGRESS: 5, DONE: 25, CANCELLED: 2 },
  byType: { PREVENTIVE: 20, CORRECTIVE: 15, INSPECTION: 7 },
};

function makeService(mttr = MOCK_MTTR, wo = MOCK_WO) {
  return {
    getMttrTrend: vi.fn().mockReturnValue(of(mttr)),
    getWoSummary: vi.fn().mockReturnValue(of(wo)),
  };
}

describe('MaintenanceAnalyticsComponent', () => {
  let fixture: ComponentFixture<MaintenanceAnalyticsComponent>;
  let component: MaintenanceAnalyticsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MaintenanceAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: makeService() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MaintenanceAnalyticsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render months period dropdown with default 6', () => {
    const select = fixture.nativeElement.querySelector('[data-testid="months-select"]');
    expect(select).toBeTruthy();
    expect(component.selectedMonths()).toBe(6);
  });

  it('should show MTTR chart when data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="mttr-chart"]');
    expect(chart).toBeTruthy();
  });

  it('should show status doughnut chart when WO data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="status-chart"]');
    expect(chart).toBeTruthy();
  });

  it('should show type doughnut chart when WO data is available', () => {
    const chart = fixture.nativeElement.querySelector('[data-testid="type-chart"]');
    expect(chart).toBeTruthy();
  });

  it('should compute mttrLabels from API data', () => {
    expect(component.mttrLabels()).toEqual(['2026-01', '2026-02', '2026-03']);
  });

  it('should compute statusLabels from WO data', () => {
    expect(component.statusLabels()).toEqual(['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED']);
  });

  it('should compute typeLabels from WO data', () => {
    expect(component.typeLabels()).toEqual(['PREVENTIVE', 'CORRECTIVE', 'INSPECTION']);
  });

  it('should map status OPEN to teal color', () => {
    const colors = component.statusColors();
    expect(colors[0]).toBe('#0099B8');
  });

  it('should map status DONE to green color', () => {
    const colors = component.statusColors();
    const doneIndex = component.statusLabels().indexOf('DONE');
    expect(colors[doneIndex]).toBe('#38A169');
  });

  it('should reload MTTR when months selection changes', () => {
    const service = TestBed.inject(AnalyticsService) as any;
    const select = fixture.nativeElement.querySelector('[data-testid="months-select"]');
    select.value = '12';
    select.dispatchEvent(new Event('change'));
    expect(service.getMttrTrend).toHaveBeenCalledWith(12);
  });

  it('should show empty MTTR state when no data', async () => {
    const emptyMttr = { monthLabels: [], mttrValues: [] };
    const service = {
      getMttrTrend: vi.fn().mockReturnValue(of(emptyMttr)),
      getWoSummary: vi.fn().mockReturnValue(of({ byStatus: {}, byType: {} })),
    };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MaintenanceAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(MaintenanceAnalyticsComponent);
    f.detectChanges();

    expect(f.nativeElement.querySelector('[data-testid="empty-mttr"]')).toBeTruthy();
    expect(f.nativeElement.querySelector('[data-testid="empty-wo"]')).toBeTruthy();
  });

  it('should show error messages on API failure', async () => {
    const service = {
      getMttrTrend: vi.fn().mockReturnValue(throwError(() => new Error())),
      getWoSummary: vi.fn().mockReturnValue(throwError(() => new Error())),
    };

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MaintenanceAnalyticsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnalyticsService, useValue: service },
      ],
    }).compileComponents();

    const f = TestBed.createComponent(MaintenanceAnalyticsComponent);
    f.detectChanges();

    const errors = f.nativeElement.querySelectorAll('.error-msg');
    expect(errors.length).toBe(2);
  });
});
