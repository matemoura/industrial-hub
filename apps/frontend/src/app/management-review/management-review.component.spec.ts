import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ManagementReviewComponent } from './management-review.component';
import { AuthService } from '../auth/auth.service';

const MOCK_DATA = {
  ncSummary: { totalReported: 10, criticalOpen: 5, avgResolutionDays: 3.5, byStatus: {}, bySeverity: {} },
  capaSummary: { totalOpen: 3, overdueCount: 2, effectivenessRate: 80.0 },
  complaintSummary: { totalReceived: 2, reportedToAnvisa: 1, avgResolutionDays: 7.0 },
  auditSummary: { completed: 3, plannedNotDone: 0, overdueAudits: 0, nonConformingFindings: 1, conformityRate: 90.0 },
  calibrationSummary: { overdueSchedules: 0, outOfToleranceCount: 0, complianceRate: 100.0 },
  trainingSummary: { fullyCompliant: 35, partiallyCompliant: 3, nonCompliant: 2, expiringIn30Days: 1 },
  riskSummary: { totalRisks: 8, criticalOpen: 0, mitigatedInPeriod: 2, avgRpn: 45.0 },
  changeSummary: { submitted: 2, approved: 3, rejected: 0, implemented: 1, pending: 1 },
  kpiSummary: { oee30Days: 72.5, openNcs: 3, openWorkOrders: 4 },
};

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

describe('ManagementReviewComponent', () => {
  let fixture: ComponentFixture<ManagementReviewComponent>;
  let component: ManagementReviewComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    TestBed.resetTestingModule();

    await TestBed.configureTestingModule({
      imports: [ManagementReviewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: makeAuth('ADMIN') },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ManagementReviewComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('seção Qualidade renderiza card NC Críticas Abertas com dados mockados', () => {
    component.data.set(MOCK_DATA as any);
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('NC Críticas Abertas');
  });

  it('chip vermelho quando criticalOpen > 3', () => {
    component.data.set(MOCK_DATA as any); // criticalOpen = 5
    fixture.detectChanges();
    expect(component.ncStatus()).toBe('red');
  });

  it('botão Exportar PDF disabled antes de carregar dados', () => {
    component.data.set(null);
    fixture.detectChanges();
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    const exportBtn = Array.from(buttons).find((b) => b.textContent?.trim().includes('Exportar PDF'));
    expect(exportBtn?.disabled).toBe(true);
  });

  it('exportPdf dispara requisição GET ao clicar', () => {
    component.data.set(MOCK_DATA as any);
    component.fromDate.set('2026-01-01');
    component.toDate.set('2026-12-31');
    fixture.detectChanges();

    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    const exportBtn = Array.from(buttons).find((b) => b.textContent?.trim().includes('Exportar PDF'));
    exportBtn?.click();

    const req = httpMock.expectOne((r) => r.url.includes('/indicators/export'));
    expect(req.request.method).toBe('GET');
    req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
  });

  it('SemaphoreChip renderiza cor correta — green chip tem classe semaphore-chip--green', () => {
    component.data.set({ ...MOCK_DATA, ncSummary: { ...MOCK_DATA.ncSummary, criticalOpen: 0 } } as any);
    fixture.detectChanges();
    const chip = fixture.nativeElement.querySelector('.semaphore-chip--green');
    expect(chip).toBeTruthy();
  });
});
