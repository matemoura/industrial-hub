import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { RiskListComponent } from './risk-list.component';
import { AuthService } from '../../../auth/auth.service';
import { RiskSummary, PageResponse, RiskItem } from '../../risk.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

const EMPTY_PAGE: PageResponse<RiskItem> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

const SAMPLE_SUMMARY: RiskSummary = {
  totalRisks: 10,
  criticalCount: 2,
  highCount: 3,
  mediumCount: 4,
  lowCount: 1,
  byStatus: {
    IDENTIFIED: 3,
    BEING_MITIGATED: 4,
    MITIGATED: 2,
    ACCEPTED: 1,
  },
  avgRpn: 125,
  topRisks: [],
};

const SAMPLE_RISKS: RiskItem[] = [
  {
    id: 'r1',
    process: 'Esterilização',
    failureMode: 'Falha no ciclo de vapor',
    failureEffect: 'Produto não estéril',
    failureCause: 'Sensor defeituoso',
    severity: 9,
    occurrence: 5,
    detectability: 6,
    rpn: 270,
    riskLevel: 'CRITICAL',
    status: 'IDENTIFIED',
    owner: 'Carlos Silva',
    createdAt: '2024-01-15T10:00:00',
  },
  {
    id: 'r2',
    process: 'Embalagem',
    failureMode: 'Selagem incompleta',
    failureEffect: 'Contaminação',
    failureCause: 'Temperatura baixa',
    severity: 7,
    occurrence: 4,
    detectability: 3,
    rpn: 84,
    riskLevel: 'MEDIUM',
    status: 'BEING_MITIGATED',
    owner: 'Ana Souza',
    createdAt: '2024-01-16T10:00:00',
  },
];

describe('RiskListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [RiskListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RiskListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  function flushInitial(risks: RiskItem[] = SAMPLE_RISKS): void {
    httpTesting
      .expectOne((req) => req.url.includes('/summary'))
      .flush(SAMPLE_SUMMARY);
    httpTesting
      .expectOne((req) => req.url.includes('/qms/risks') && !req.url.includes('summary') && !req.url.includes('matrix'))
      .flush({ ...EMPTY_PAGE, content: risks, totalElements: risks.length });
  }

  // (a) chip CRITICAL renderiza com background #D24A4A
  it('chip_critical_tem_cor_danger', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial();
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll('.level-chip');
    const criticalChip = Array.from(chips).find(
      (el: unknown) => (el as HTMLElement).textContent?.trim() === 'Crítico'
    ) as HTMLElement | undefined;

    expect(criticalChip).not.toBeUndefined();
    expect(criticalChip!.style.backgroundColor).toMatch(/rgb\(210,\s*74,\s*74\)|#D24A4A/i);
  });

  // (b) RPN > 200 exibido em vermelho na tabela
  it('rpn_acima_200_exibido_em_vermelho', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial();
    fixture.detectChanges();

    const rpnCells = fixture.nativeElement.querySelectorAll('.cell-rpn');
    const highRpnCell = Array.from(rpnCells).find(
      (el: unknown) => (el as HTMLElement).textContent?.trim() === '270'
    ) as HTMLElement | undefined;

    expect(highRpnCell).not.toBeUndefined();
    expect(highRpnCell!.style.color).toMatch(/rgb\(210,\s*74,\s*74\)|#D24A4A/i);
  });

  // (c) preview RPN atualiza quando severity muda
  it('preview_rpn_atualiza_ao_mudar_severity', () => {
    const fixture = setup('SUPERVISOR');
    flushInitial();
    fixture.detectChanges();

    // Abrir modal
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-risk"]');
    btn.click();
    fixture.detectChanges();

    const previewEl = fixture.nativeElement.querySelector('[data-testid="preview-rpn"]');
    // Valor inicial: 5 * 5 * 5 = 125
    expect(previewEl.textContent.trim()).toBe('125');

    // Mudar severity para 8 via signal direto
    fixture.componentInstance.newSeverity.set(8);
    fixture.detectChanges();

    // 8 * 5 * 5 = 200
    expect(previewEl.textContent.trim()).toBe('200');
  });

  // (d) botão "Novo Risco" oculto para OPERATOR
  it('botao_novo_risco_oculto_para_operator', () => {
    const fixture = setup('OPERATOR');
    flushInitial([]);
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-risk"]');
    expect(btn).toBeNull();
  });
});
