import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { ProductionReportComponent } from './production-report.component';
import { PlanningSummaryRow } from '../production.service';
import { AuthService } from '../../auth/auth.service';

const mockRows: PlanningSummaryRow[] = [
  {
    familyCode: 'FAM-A', familyName: 'Família A',
    productCode: 'P001', productName: 'Produto X',
    plannedQty: 100, producedQty: 80, efficiency: 80.0, pendingMrpQty: 0,
  },
  {
    familyCode: 'FAM-A', familyName: 'Família A',
    productCode: 'P002', productName: 'Produto Y',
    plannedQty: 0, producedQty: 0, efficiency: null, pendingMrpQty: 20,
  },
];

describe('ProductionReportComponent', () => {
  let fixture: ComponentFixture<ProductionReportComponent>;
  let component: ProductionReportComponent;
  let httpMock: import('@angular/common/http/testing').HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductionReportComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { role: signal('SUPERVISOR') },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductionReportComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // Flush families GET
    const req = httpMock.expectOne((r) => r.url.includes('/families'));
    req.flush([]);
  });

  afterEach(() => httpMock.verify());

  it('should render table with rows after load', () => {
    component.fromDate.set('2026-01-01');
    component.toDate.set('2026-01-31');
    component.load();

    const req = httpMock.expectOne((r) => r.url.includes('/reports/planning-summary'));
    req.flush(mockRows);
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('[data-testid="report-table"]');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
  });

  it('should display — for null efficiency', () => {
    component.rows.set(mockRows);
    fixture.detectChanges();

    const cells = fixture.nativeElement.querySelectorAll('[data-testid="report-table"] tbody tr td');
    // 2nd row efficiency (null) → "—"
    const row2Cells = fixture.nativeElement.querySelectorAll(
      '[data-testid="report-table"] tbody tr:nth-child(2) td',
    );
    // efficiency column is 6th (index 5)
    const efficiencyCell = row2Cells[5];
    expect(efficiencyCell?.textContent?.trim()).toBe('—');
  });

  it('should disable export button when no results', () => {
    component.rows.set([]);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-export"]');
    expect(btn?.disabled).toBe(true);
  });

  it('should enable export button when results exist', () => {
    component.rows.set(mockRows);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-export"]');
    expect(btn?.disabled).toBe(false);
  });

  it('should call window.open with noopener,noreferrer on export', () => {
    component.rows.set(mockRows);
    component.fromDate.set('2026-01-01');
    component.toDate.set('2026-01-31');
    fixture.detectChanges();

    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    component.exportCsv();

    expect(openSpy).toHaveBeenCalledWith(
      expect.stringContaining('/reports/planning-summary/export'),
      '_blank',
      'noopener,noreferrer',
    );
    openSpy.mockRestore();
  });

  it('should show error message on load failure', () => {
    component.fromDate.set('2026-01-01');
    component.toDate.set('2026-01-31');
    component.load();

    const req = httpMock.expectOne((r) => r.url.includes('/reports/planning-summary'));
    req.flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.alert-error');
    expect(alert).toBeTruthy();
  });
});
