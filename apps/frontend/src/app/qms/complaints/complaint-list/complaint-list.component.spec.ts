import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ComplaintListComponent } from './complaint-list.component';
import { AuthService } from '../../../auth/auth.service';
import { ComplaintIndicators, PageResponse, Complaint } from '../../complaints.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

const EMPTY_PAGE: PageResponse<Complaint> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

const SAMPLE_INDICATORS: ComplaintIndicators = {
  totalReceived: 10,
  byStatus: {
    RECEIVED: 2,
    UNDER_INVESTIGATION: 3,
    INVESTIGATION_COMPLETED: 1,
    CLOSED: 4,
  },
  bySeverity: { LOW: 2, MEDIUM: 3, HIGH: 4, CRITICAL: 1 },
  reportedToAnvisa: 4,
  avgResolutionDays: 12.5,
  byProduct: [{ productCode: 'PROD-001', count: 5 }],
  bySource: { CLIENT: 6, DISTRIBUTOR: 2, REGULATORY_BODY: 1, INTERNAL: 1 },
};

describe('ComplaintListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [ComplaintListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ComplaintListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  function flushInitial(indicators = SAMPLE_INDICATORS): void {
    httpTesting
      .expectOne((req) => req.url.includes('/indicators'))
      .flush(indicators);
    httpTesting
      .expectOne((req) => req.url.includes('/qms/complaints') && !req.url.includes('/indicators'))
      .flush(EMPTY_PAGE);
  }

  // (a) Botão "Registrar Notificação" (ANVISA) não existe na tela de lista
  it('botao_registrar_notificacao_anvisa_nao_existe_na_lista', () => {
    const fixture = setup('ADMIN');
    flushInitial();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-anvisa-report"]');
    expect(btn).toBeNull();
  });

  // (b) Card "Não Reportadas" tem classe kpi-card--danger quando count > 0
  it('card_nao_reportadas_danger_quando_count_maior_zero', () => {
    // totalReceived=10, reportedToAnvisa=4 → notReportedCount=6 > 0
    const fixture = setup('SUPERVISOR');
    flushInitial(SAMPLE_INDICATORS);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-testid="card-not-reported"]');
    expect(card).not.toBeNull();
    expect(card.classList.contains('kpi-card--danger')).toBeTruthy();
  });

  // (c) Card "Não Reportadas" SEM classe danger quando count = 0
  it('card_nao_reportadas_sem_danger_quando_count_zero', () => {
    const allReported: ComplaintIndicators = { ...SAMPLE_INDICATORS, totalReceived: 4, reportedToAnvisa: 4 };
    const fixture = setup('SUPERVISOR');
    flushInitial(allReported);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-testid="card-not-reported"]');
    expect(card).not.toBeNull();
    expect(card.classList.contains('kpi-card--danger')).toBeFalsy();
  });

  // (d) Botão "Nova Reclamação" visível para SUPERVISOR
  it('botao_nova_reclamacao_visivel_para_supervisor', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-complaint"]');
    expect(btn).not.toBeNull();
  });

  // (e) KPI total exibe totalReceived
  it('kpi_total_exibe_total_received', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial(SAMPLE_INDICATORS);
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('[data-testid="kpi-total"]');
    expect(el).not.toBeNull();
    expect(el.textContent.trim()).toBe('10');
  });
});
