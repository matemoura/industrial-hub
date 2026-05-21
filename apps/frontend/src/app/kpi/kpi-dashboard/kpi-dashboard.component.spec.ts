import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { KpiDashboardComponent } from './kpi-dashboard.component';
import { KpiService, KpiSummaryResponse } from '../kpi.service';
import { SlaService, SlaSummaryResponse } from '../../admin/sla-rules/sla.service';

const MOCK_KPI: KpiSummaryResponse = {
  oeeAvgLast30Days: 0.72,
  totalNcOpen: 3,
  totalNcCritical: 1,
  totalWorkOrdersOpen: 5,
  mttrGlobalHours: 2.4,
  activeEquipmentCount: 10,
};

const MOCK_SLA_SUMMARY: SlaSummaryResponse = {
  totalBreachedNcs: 2,
  totalBreachedWorkOrders: 1,
  totalOpenNcs: 5,
  totalOpenWorkOrders: 10,
};

function makeKpiService(kpi: KpiSummaryResponse = MOCK_KPI) {
  return {
    getSummary: vi.fn().mockReturnValue(of(kpi)),
  };
}

function makeSlaService(summary: SlaSummaryResponse = MOCK_SLA_SUMMARY) {
  return {
    getSlaSummary: vi.fn().mockReturnValue(of(summary)),
    listSlaRules: vi.fn().mockReturnValue(of([])),
    createSlaRule: vi.fn(),
    updateSlaRule: vi.fn(),
    deleteSlaRule: vi.fn(),
    runEscalationNow: vi.fn(),
  };
}

async function setup(kpi = MOCK_KPI, sla = MOCK_SLA_SUMMARY) {
  const kpiService = makeKpiService(kpi);
  const slaService = makeSlaService(sla);

  await TestBed.configureTestingModule({
    imports: [KpiDashboardComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: KpiService, useValue: kpiService },
      { provide: SlaService, useValue: slaService },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(KpiDashboardComponent);
  fixture.detectChanges();
  return { fixture, kpiService, slaService };
}

describe('KpiDashboardComponent', () => {
  describe('KPI cards', () => {
    it('should display OEE card', async () => {
      const { fixture } = await setup();
      const card = fixture.nativeElement.querySelector('[data-testid="kpi-card-oee"]');
      expect(card).toBeTruthy();
      expect(card.textContent).toContain('72.0%');
    });

    it('should display open NCs card', async () => {
      const { fixture } = await setup();
      const card = fixture.nativeElement.querySelector('[data-testid="kpi-card-ncs-abertas"]');
      expect(card).toBeTruthy();
      expect(card.textContent).toContain('3');
    });
  });

  describe('SLA Risk Panel — AC-19', () => {
    it('should show sla-risk-panel when totalBreachedNcs > 0', async () => {
      const { fixture } = await setup(MOCK_KPI, { ...MOCK_SLA_SUMMARY, totalBreachedNcs: 2, totalBreachedWorkOrders: 0 });
      const panel = fixture.nativeElement.querySelector('[data-testid="sla-risk-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should show sla-risk-panel when totalBreachedWorkOrders > 0', async () => {
      const { fixture } = await setup(MOCK_KPI, { ...MOCK_SLA_SUMMARY, totalBreachedNcs: 0, totalBreachedWorkOrders: 1 });
      const panel = fixture.nativeElement.querySelector('[data-testid="sla-risk-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should hide sla-risk-panel when both counters are 0', async () => {
      const { fixture } = await setup(MOCK_KPI, { totalBreachedNcs: 0, totalBreachedWorkOrders: 0, totalOpenNcs: 0, totalOpenWorkOrders: 0 });
      const panel = fixture.nativeElement.querySelector('[data-testid="sla-risk-panel"]');
      expect(panel).toBeFalsy();
    });

    it('should display breachedNcs count in panel', async () => {
      const { fixture } = await setup(MOCK_KPI, MOCK_SLA_SUMMARY);
      const card = fixture.nativeElement.querySelector('[data-testid="sla-risk-ncs"]');
      expect(card).toBeTruthy();
      expect(card.textContent).toContain('2');
    });

    it('should display breachedWorkOrders count in panel', async () => {
      const { fixture } = await setup(MOCK_KPI, MOCK_SLA_SUMMARY);
      const card = fixture.nativeElement.querySelector('[data-testid="sla-risk-wos"]');
      expect(card).toBeTruthy();
      expect(card.textContent).toContain('1');
    });

    it('should hide panel when sla summary API fails', async () => {
      const kpiService = makeKpiService();
      const slaService = {
        ...makeSlaService(),
        getSlaSummary: vi.fn().mockReturnValue(throwError(() => new Error())),
      };
      await TestBed.configureTestingModule({
        imports: [KpiDashboardComponent],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: KpiService, useValue: kpiService },
          { provide: SlaService, useValue: slaService },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(KpiDashboardComponent);
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="sla-risk-panel"]');
      expect(panel).toBeFalsy();
    });
  });
});
