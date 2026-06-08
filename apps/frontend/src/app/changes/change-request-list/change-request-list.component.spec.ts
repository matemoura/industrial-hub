import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ChangeRequestListComponent } from './change-request-list.component';
import { AuthService } from '../../auth/auth.service';
import { PageResponse, ChangeRequest } from '../change-request.service';

function makeAuth(role: string | null) {
  return {
    role: signal(role),
    username: signal('testuser'),
  };
}

const EMPTY_PAGE: PageResponse<ChangeRequest> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

const SAMPLE_PAGE: PageResponse<ChangeRequest> = {
  content: [
    {
      id: 'cr-001',
      code: 'CR-2026-001',
      title: 'Mudança de Processo',
      description: 'Desc',
      changeType: 'PROCESS',
      justification: 'Justi',
      status: 'SUBMITTED',
      requestedBy: 'testuser',
      createdAt: '2026-06-01T08:00:00',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

describe('ChangeRequestListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'OPERATOR') {
    TestBed.configureTestingModule({
      imports: [ChangeRequestListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ChangeRequestListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  function flushSummaryAndList(listPage: PageResponse<ChangeRequest> = EMPTY_PAGE): void {
    // 5 summary requests (one per status) + 1 list request
    const summaryStatuses = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'IMPLEMENTED'];
    for (const status of summaryStatuses) {
      const req = httpTesting.expectOne((r) => r.url.includes('/api/v1/changes') && r.params.get('status') === status);
      req.flush(EMPTY_PAGE);
    }
    httpTesting
      .expectOne((r) => r.url.includes('/api/v1/changes') && !r.params.has('status'))
      .flush(listPage);
  }

  // (a) Botão "Nova Solicitação" visível para OPERATOR
  it('botao_nova_solicitacao_visivel_para_operator', () => {
    const fixture = setup('OPERATOR');
    flushSummaryAndList();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-change"]');
    expect(btn).not.toBeNull();
  });

  // (b) Chip SUBMITTED renderiza cor correta (#56A4BB)
  it('chip_submitted_tem_cor_azul', () => {
    const fixture = setup('OPERATOR');
    flushSummaryAndList(SAMPLE_PAGE);
    fixture.detectChanges();

    const chip = fixture.nativeElement.querySelector('.chip--status');
    expect(chip).not.toBeNull();
    expect(chip.style.color).toBeTruthy();
    // The color for SUBMITTED should be #56A4BB (rgb(86, 164, 187))
    expect(chip.style.color).toContain('rgb(86, 164, 187)');
  });

  // (c) Filtro de status dispara nova requisição
  it('filtro_status_dispara_requisicao', () => {
    const fixture = setup('SUPERVISOR');
    flushSummaryAndList();
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    comp.statusFilter = 'DRAFT';
    comp.onFilterChange();

    // Expect a new list request filtered by status=DRAFT
    const req = httpTesting.expectOne(
      (r) => r.url.includes('/api/v1/changes') && r.params.get('status') === 'DRAFT',
    );
    expect(req.request.params.get('status')).toBe('DRAFT');
    req.flush(EMPTY_PAGE);
  });
});
