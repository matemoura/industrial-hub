import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { AgingDashboardComponent } from './aging-dashboard.component';
import { QmsService, CapaAgingResponse } from '../qms.service';

const mockAging: CapaAgingResponse = {
  totalOpen: 20,
  overdueCount: 5,
  avgResolutionDaysOpen: 12.5,
  noDueDateCount: 3,
  bucket0to7: { count: 4, label: '0–7 dias' },
  bucket8to15: { count: 6, label: '8–15 dias' },
  bucket16to30: { count: 3, label: '16–30 dias' },
  bucketOver30: { count: 2, label: '>30 dias', overdueCount: 0 },
  overdueByNcSeverity: [
    { severity: 'CRITICAL', overdueCount: 2 },
    { severity: 'HIGH', overdueCount: 3 },
  ],
};

describe('AgingDashboardComponent', () => {
  let fixture: ComponentFixture<AgingDashboardComponent>;
  let component: AgingDashboardComponent;
  let qmsService: { getCapaAging: ReturnType<typeof vi.fn>; exportCapaAgingCsv: ReturnType<typeof vi.fn> };

  function setup(agingData: CapaAgingResponse | null = mockAging, error = false) {
    qmsService = {
      getCapaAging: vi.fn().mockReturnValue(
        error ? throwError(() => ({ error: { message: 'Server error' } })) : of(agingData)
      ),
      exportCapaAgingCsv: vi.fn().mockReturnValue(of(new Blob(['csv']))),
    };

    TestBed.configureTestingModule({
      imports: [AgingDashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QmsService, useValue: qmsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AgingDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // US-116-AC1: 4 summary cards rendered
  it('should show 4 summary cards with correct values', () => {
    setup();
    const compiled = fixture.nativeElement as HTMLElement;
    const totalCard = compiled.querySelector('[data-testid="card-total-open"]');
    const overdueCard = compiled.querySelector('[data-testid="card-overdue"]');
    const noDueDateCard = compiled.querySelector('[data-testid="card-no-due-date"]');
    const avgCard = compiled.querySelector('[data-testid="card-avg-days"]');

    expect(totalCard?.textContent).toContain('20');
    expect(overdueCard?.textContent).toContain('5');
    expect(noDueDateCard?.textContent).toContain('3');
    expect(avgCard?.textContent).toContain('13');
  });

  // US-116-AC2: chart labels correspond to bucket labels
  it('should compute chart labels from buckets', () => {
    setup();
    expect(component.chartLabels()).toEqual(['0–7 dias', '8–15 dias', '16–30 dias', '>30 dias']);
    expect(component.chartValues()).toEqual([4, 6, 3, 2]);
  });

  // US-116-AC3: export button triggers download
  it('should call exportCapaAgingCsv on export button click', () => {
    setup();
    const exportBtn = fixture.nativeElement.querySelector('[data-testid="btn-export-csv"]') as HTMLButtonElement;
    exportBtn.click();
    fixture.detectChanges();
    expect(qmsService.exportCapaAgingCsv.mock.calls.length).toBe(1);
  });

  // US-116-AC4: error state shown when API fails
  it('should show error banner when API returns error', () => {
    setup(null, true);
    const compiled = fixture.nativeElement as HTMLElement;
    const banner = compiled.querySelector('.error-banner');
    expect(banner).toBeTruthy();
  });

  // US-116-AC5: avg days label formatting
  it('should format avgResolutionDaysOpen correctly', () => {
    setup();
    expect(component.avgDaysLabel()).toBe('13 dias');
  });
});
