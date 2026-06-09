import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ComplaintDetailComponent } from './complaint-detail.component';
import { AuthService } from '../../../auth/auth.service';
import { Complaint } from '../../complaints.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

function makeRoute(id: string) {
  return {
    snapshot: { paramMap: { get: () => id } },
  };
}

const COMPLAINT_RECEIVED: Complaint = {
  id: 'uuid-001',
  code: 'REC-2026-001',
  title: 'Reclamação Teste',
  description: 'Descrição da reclamação',
  source: 'CLIENT',
  productCode: 'PROD-001',
  batchNumber: 'LOTE-001',
  severity: 'HIGH',
  status: 'RECEIVED',
  reportedDate: '2026-01-15',
  reportedBy: 'Hospital São Lucas',
  assignedTo: 'qualidade',
  reportedToAnvisa: false,
  createdAt: '2026-01-15T10:00:00',
};

const COMPLAINT_CLOSED_ANVISA: Complaint = {
  ...COMPLAINT_RECEIVED,
  id: 'uuid-002',
  status: 'CLOSED',
  reportedToAnvisa: true,
  anvisaReportNumber: 'ANVISA-2026-001',
  anvisaReportDate: '2026-02-01',
  investigationSummary: 'Investigação completa',
  rootCause: 'Falha no processo',
  closedAt: '2026-02-15T10:00:00',
};

const COMPLAINT_WITH_NC: Complaint = {
  ...COMPLAINT_RECEIVED,
  linkedNcId: 'nc-uuid-123',
  linkedNcCode: 'NC-2026-010',
};

describe('ComplaintDetailComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(complaint: Complaint, role = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [ComplaintDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
        { provide: ActivatedRoute, useValue: makeRoute(complaint.id) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ComplaintDetailComponent);
    fixture.detectChanges();
    httpTesting
      .expectOne((req) => req.url.includes(`/qms/complaints/${complaint.id}`))
      .flush(complaint);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  // (a) Botão "Registrar Notificação" visível apenas para ADMIN
  it('btn_registrar_notificacao_visivel_somente_para_admin', () => {
    const fixtureAdmin = setup(COMPLAINT_RECEIVED, 'ADMIN');
    const btnAdmin = fixtureAdmin.nativeElement.querySelector('[data-testid="btn-anvisa-report"]');
    expect(btnAdmin).not.toBeNull();

    TestBed.resetTestingModule();

    const fixtureSupervisor = setup(COMPLAINT_RECEIVED, 'SUPERVISOR');
    const btnSupervisor = fixtureSupervisor.nativeElement.querySelector('[data-testid="btn-anvisa-report"]');
    expect(btnSupervisor).toBeNull();
  });

  // (b) Botão "Gerar MDR" oculto quando reportedToAnvisa=false
  it('btn_gerar_mdr_oculto_quando_reported_to_anvisa_false', () => {
    const fixture = setup(COMPLAINT_RECEIVED, 'ADMIN');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-generate-mdr"]');
    expect(btn).toBeNull();
  });

  // (b2) Botão "Gerar MDR" visível quando ADMIN + reportedToAnvisa=true + CLOSED
  it('btn_gerar_mdr_visivel_quando_admin_anvisa_e_closed', () => {
    const fixture = setup(COMPLAINT_CLOSED_ANVISA, 'ADMIN');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-generate-mdr"]');
    expect(btn).not.toBeNull();
  });

  // (b3) Botão "Gerar MDR" oculto para SUPERVISOR mesmo com reportedToAnvisa=true e CLOSED
  it('btn_gerar_mdr_oculto_para_supervisor', () => {
    const fixture = setup(COMPLAINT_CLOSED_ANVISA, 'SUPERVISOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-generate-mdr"]');
    expect(btn).toBeNull();
  });

  // (c) "Iniciar Investigação" visível quando status=RECEIVED; oculto quando CLOSED
  it('btn_transicao_visivel_quando_received_oculto_quando_closed', () => {
    const fixture = setup(COMPLAINT_RECEIVED, 'SUPERVISOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-transition"]');
    expect(btn).not.toBeNull();
    expect(btn.textContent.trim()).toContain('Iniciar Investigação');

    TestBed.resetTestingModule();

    const fixtureClosed = setup(COMPLAINT_CLOSED_ANVISA, 'SUPERVISOR');
    const btnClosed = fixtureClosed.nativeElement.querySelector('[data-testid="btn-transition"]');
    expect(btnClosed).toBeNull();
  });

  // (d) NC vinculada exibe código + routerLink /qms/non-conformances/{id}
  it('nc_vinculada_exibe_codigo_e_routerlink', () => {
    const fixture = setup(COMPLAINT_WITH_NC, 'SUPERVISOR');
    const ncLink = fixture.nativeElement.querySelector('[data-testid="nc-link"]');
    expect(ncLink).not.toBeNull();
    expect(ncLink.textContent.trim()).toContain('NC-2026-010');
    const href = ncLink.getAttribute('href');
    expect(href).toContain('/qms/non-conformances/nc-uuid-123');
  });
});
