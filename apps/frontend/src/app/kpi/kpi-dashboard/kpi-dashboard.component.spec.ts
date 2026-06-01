import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { KpiDashboardComponent } from './kpi-dashboard.component';
import { KpiService, KpiSummaryResponse } from '../kpi.service';
import { AuthService } from '../../auth/auth.service';

const MOCK_KPI: KpiSummaryResponse = {
  oeeAvgLast30Days:    0.78,
  totalNcOpen:         12,
  totalNcCritical:     3,
  totalWorkOrdersOpen: 7,
  mttrGlobalHours:     2.4,
  activeEquipmentCount: 18,
};

function makeKpiService(data: Partial<KpiSummaryResponse> = {}): Partial<KpiService> {
  return { getSummary: vi.fn().mockReturnValue(of({ ...MOCK_KPI, ...data })) };
}

function makeAuthService(role = 'OPERATOR'): Partial<AuthService> {
  return { role: signal(role) as AuthService['role'] };
}

describe('KpiDashboardComponent', () => {
  let fixture: ComponentFixture<KpiDashboardComponent>;
  let component: KpiDashboardComponent;

  async function setup(
    kpiSvc: Partial<KpiService>  = makeKpiService(),
    authSvc: Partial<AuthService> = makeAuthService(),
  ) {
    await TestBed.configureTestingModule({
      imports: [KpiDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: KpiService,  useValue: kpiSvc  },
        { provide: AuthService, useValue: authSvc },
      ],
    }).compileComponents();

    fixture   = TestBed.createComponent(KpiDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should render OEE gauge after data loads', async () => {
    await setup();
    const gauge = fixture.nativeElement.querySelector('[data-testid="gauge-oee-global"]');
    expect(gauge).toBeTruthy();
  });

  it('should display NC open count in kpi-nc-open card', async () => {
    await setup();
    const card = fixture.nativeElement.querySelector('[data-testid="kpi-nc-open"]');
    expect(card?.textContent).toContain('12');
  });

  it('should highlight NC value as danger when criticals > 0', async () => {
    await setup();
    const value = fixture.nativeElement.querySelector('[data-testid="kpi-nc-open"] .kpi-side__value');
    expect(value?.classList).toContain('kpi-side__value--danger');
  });

  it('should NOT highlight NC value as danger when criticals = 0', async () => {
    await setup(makeKpiService({ totalNcCritical: 0 }));
    const value = fixture.nativeElement.querySelector('[data-testid="kpi-nc-open"] .kpi-side__value');
    expect(value?.classList).not.toContain('kpi-side__value--danger');
  });

  it('should show work orders count in kpi-wo-open card', async () => {
    await setup();
    const card = fixture.nativeElement.querySelector('[data-testid="kpi-wo-open"]');
    expect(card?.textContent).toContain('7');
  });

  it('should show equipment count in kpi-equipment card', async () => {
    await setup();
    const card = fixture.nativeElement.querySelector('[data-testid="kpi-equipment"]');
    expect(card?.textContent).toContain('18');
  });

  it('should display OEE as percentage string', async () => {
    await setup();
    expect(component.oeeDisplay()).toBe('78.0%');
  });

  it('should flag oeeOk when oee >= 65%', async () => {
    await setup(makeKpiService({ oeeAvgLast30Days: 0.65 }));
    expect(component.oeeOk()).toBe(true);
  });

  it('should flag oeeOk as false when oee < 65%', async () => {
    await setup(makeKpiService({ oeeAvgLast30Days: 0.60 }));
    expect(component.oeeOk()).toBe(false);
  });

  it('should show "Nova NC" button only for SUPERVISOR/ADMIN', async () => {
    await setup(makeKpiService(), makeAuthService('OPERATOR'));
    expect(fixture.nativeElement.querySelector('a[routerlink="/qms/non-conformances/new"]')).toBeFalsy();

    await setup(makeKpiService(), makeAuthService('SUPERVISOR'));
    expect(fixture.nativeElement.querySelector('a[routerlink="/qms/non-conformances/new"]')).toBeTruthy();
  });

  it('should show error message when API call fails', async () => {
    const failSvc: Partial<KpiService> = {
      getSummary: vi.fn().mockReturnValue(throwError(() => new Error('fail'))),
    };
    await setup(failSvc);
    expect(fixture.nativeElement.querySelector('.sp-error')).toBeTruthy();
  });

  it('should render the MSB hero block', async () => {
    await setup();
    const hero = fixture.nativeElement.querySelector('[data-testid="kpi-dashboard"]');
    expect(hero?.querySelector('.msb-hero')).toBeTruthy();
  });
});
