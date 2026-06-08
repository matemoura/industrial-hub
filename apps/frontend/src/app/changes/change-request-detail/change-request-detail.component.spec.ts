import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { ChangeRequestDetailComponent } from './change-request-detail.component';
import { AuthService } from '../../auth/auth.service';
import { ChangeRequestDetail } from '../change-request.service';

function makeAuth(role: string | null, username = 'testuser') {
  return {
    role: signal(role),
    username: signal(username),
  };
}

function makeActivatedRoute(id: string) {
  return {
    snapshot: { paramMap: { get: (_: string) => id } },
  };
}

function makeCR(overrides: Partial<ChangeRequestDetail> = {}): ChangeRequestDetail {
  return {
    id: 'cr-001',
    code: 'CR-2026-001',
    title: 'Mudança de Processo Teste',
    description: 'Descrição da mudança',
    changeType: 'PROCESS',
    justification: 'Necessário por auditoria',
    status: 'DRAFT',
    requestedBy: 'testuser',
    createdAt: '2026-06-01T08:00:00',
    links: [],
    ...overrides,
  };
}

describe('ChangeRequestDetailComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(
    role: string | null = 'OPERATOR',
    username = 'testuser',
    crId = 'cr-001',
  ) {
    TestBed.configureTestingModule({
      imports: [ChangeRequestDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role, username) },
        { provide: ActivatedRoute, useValue: makeActivatedRoute(crId) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ChangeRequestDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  function flushCR(detail: ChangeRequestDetail): void {
    httpTesting
      .expectOne((req) => req.url.includes('/api/v1/changes/'))
      .flush(detail);
  }

  // (a) "Submeter" visível apenas para autor em DRAFT
  it('btn_submit_visivel_para_autor_em_draft', () => {
    const fixture = setup('OPERATOR', 'testuser');
    flushCR(makeCR({ status: 'DRAFT', requestedBy: 'testuser' }));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit"]');
    expect(btn).not.toBeNull();
  });

  // "Submeter" NÃO visível para usuário diferente do autor
  it('btn_submit_nao_visivel_para_outro_usuario_em_draft', () => {
    const fixture = setup('OPERATOR', 'otheruser');
    flushCR(makeCR({ status: 'DRAFT', requestedBy: 'testuser' }));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-submit"]');
    expect(btn).toBeNull();
  });

  // (b) "Revisar" visível para SUPERVISOR em SUBMITTED
  it('btn_review_visivel_para_supervisor_em_submitted', () => {
    const fixture = setup('SUPERVISOR', 'supervisor1');
    flushCR(makeCR({ status: 'SUBMITTED', requestedBy: 'otheruser' }));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-review"]');
    expect(btn).not.toBeNull();
  });

  // "Revisar" NÃO visível para OPERATOR em SUBMITTED
  it('btn_review_nao_visivel_para_operator_em_submitted', () => {
    const fixture = setup('OPERATOR', 'testuser');
    flushCR(makeCR({ status: 'SUBMITTED', requestedBy: 'testuser' }));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-review"]');
    expect(btn).toBeNull();
  });

  // (c) "Aprovar" visível apenas para ADMIN em UNDER_REVIEW
  it('btn_approve_visivel_apenas_para_admin', () => {
    // ADMIN deve ver o botão
    const fixture = setup('ADMIN', 'admin1');
    flushCR(makeCR({ status: 'UNDER_REVIEW', requestedBy: 'otheruser' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="btn-approve"]')).not.toBeNull();

    TestBed.resetTestingModule();

    // SUPERVISOR NÃO deve ver o botão
    const fixture2 = setup('SUPERVISOR', 'sup1');
    flushCR(makeCR({ status: 'UNDER_REVIEW', requestedBy: 'otheruser' }));
    fixture2.detectChanges();

    expect(fixture2.nativeElement.querySelector('[data-testid="btn-approve"]')).toBeNull();
  });

  // (d) Link de vínculo NC exibe routerLink correto
  it('link_nc_tem_routerlink_correto', () => {
    const ncId = 'nc-uuid-abc123';
    const fixture = setup('OPERATOR', 'testuser');
    flushCR(
      makeCR({
        status: 'APPROVED',
        links: [
          {
            id: 'link-001',
            changeRequestId: 'cr-001',
            entityType: 'NON_CONFORMANCE',
            entityId: ncId,
            createdAt: '2026-06-01T08:00:00',
          },
        ],
      }),
    );
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector(
      `[data-testid="link-NON_CONFORMANCE-${ncId}"]`,
    );
    expect(link).not.toBeNull();
    expect(link.getAttribute('href')).toContain(`non-conformances/${ncId}`);
  });
});
