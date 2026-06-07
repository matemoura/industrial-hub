import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { AuditListComponent } from './audit-list.component';
import { AuthService } from '../../../auth/auth.service';
import { AuditComplianceDashboard, PageResponse, InternalAudit } from '../../audit.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

const EMPTY_PAGE: PageResponse<InternalAudit> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

const SAMPLE_DASHBOARD: AuditComplianceDashboard = {
  plannedThisYear: 10,
  completedThisYear: 7,
  overdueAudits: 2,
  openFindings: 5,
  findingsByType: {
    NON_CONFORMANCE: 3,
    OBSERVATION: 1,
    OPPORTUNITY_FOR_IMPROVEMENT: 1,
  },
  conformityRate: 85.5,
};

describe('AuditListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [AuditListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AuditListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  function flushInitial(): void {
    httpTesting
      .expectOne((req) => req.url.includes('/compliance-dashboard'))
      .flush(SAMPLE_DASHBOARD);
    httpTesting
      .expectOne((req) => req.url.includes('/qms/audits') && !req.url.includes('dashboard'))
      .flush(EMPTY_PAGE);
  }

  // (a) Botão "Nova Auditoria" visível para SUPERVISOR
  it('nova_auditoria_visivel_para_supervisor', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-audit"]');
    expect(btn).not.toBeNull();
  });

  // (b) Botão "Nova Auditoria" oculto para OPERATOR
  it('nova_auditoria_oculto_para_operator', () => {
    const fixture = setup('OPERATOR');
    flushInitial();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-audit"]');
    expect(btn).toBeNull();
  });

  // (c) Card de conformidade renderiza conformityRate
  it('card_conformidade_renderiza_conformity_rate', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial();
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('[data-testid="conformity-rate"]');
    expect(el).not.toBeNull();
    expect(el.textContent).toContain('85.5');
  });
});
