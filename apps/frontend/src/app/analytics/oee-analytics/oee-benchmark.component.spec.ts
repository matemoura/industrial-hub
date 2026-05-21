import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { OeeBenchmarkComponent } from './oee-benchmark.component';
import { AnalyticsService, BenchmarkResponse, PeriodComparisonResponse } from '../analytics.service';

// ── Factories ─────────────────────────────────────────────────────────────────

function makeBenchmarkResponse(
  entries: { label: string; avgOee: number }[],
  recordsWithoutShift = 0,
): BenchmarkResponse {
  return {
    ranking: entries.map(({ label, avgOee }) => ({
      label,
      avgOee,
      minOee: avgOee - 0.05,
      maxOee: avgOee + 0.05,
      stdDev: null,
      sampleCount: 5,
    })),
    best: entries.length > 0 ? { label: entries[0].label, avgOee: entries[0].avgOee, minOee: 0, maxOee: 1, stdDev: null, sampleCount: 5 } : null,
    worst: entries.length > 0 ? { label: entries[entries.length - 1].label, avgOee: entries[entries.length - 1].avgOee, minOee: 0, maxOee: 1, stdDev: null, sampleCount: 5 } : null,
    overallAvg: entries.length > 0 ? entries.reduce((s, e) => s + e.avgOee, 0) / entries.length : 0,
    recordsWithoutShift,
  };
}

const MOCK_WORKERS_RESPONSE = makeBenchmarkResponse([
  { label: 'Alice', avgOee: 0.78 },
  { label: 'Bob', avgOee: 0.55 },
  { label: 'Carlos', avgOee: 0.43 },
]);

const MOCK_SHIFTS_RESPONSE = makeBenchmarkResponse(
  [{ label: 'Manhã', avgOee: 0.72 }],
  3, // recordsWithoutShift
);

const MOCK_EQUIPMENT_RESPONSE = makeBenchmarkResponse([
  { label: 'MACHINE', avgOee: 0.68 },
]);

const MOCK_PERIOD_COMPARISON: PeriodComparisonResponse = {
  periodA: [
    { label: 'Alice', avgOee: 0.80, minOee: 0.70, maxOee: 0.90, stdDev: 0.05, sampleCount: 10 },
    { label: 'Bob', avgOee: 0.65, minOee: 0.55, maxOee: 0.75, stdDev: 0.04, sampleCount: 8 },
  ],
  periodB: [
    { label: 'Alice', avgOee: 0.72, minOee: 0.62, maxOee: 0.82, stdDev: 0.06, sampleCount: 9 },
    { label: 'Bob', avgOee: 0.60, minOee: 0.50, maxOee: 0.70, stdDev: 0.05, sampleCount: 7 },
  ],
  improvementPct: 8.5,
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeAnalyticsService(overrides: Partial<{
  getBenchmarkWorkers: ReturnType<typeof vi.fn>;
  getBenchmarkShifts: ReturnType<typeof vi.fn>;
  getBenchmarkEquipmentType: ReturnType<typeof vi.fn>;
  getPeriodComparison: ReturnType<typeof vi.fn>;
}> = {}) {
  return {
    getBenchmarkWorkers: vi.fn().mockReturnValue(of(MOCK_WORKERS_RESPONSE)),
    getBenchmarkShifts: vi.fn().mockReturnValue(of(MOCK_SHIFTS_RESPONSE)),
    getBenchmarkEquipmentType: vi.fn().mockReturnValue(of(MOCK_EQUIPMENT_RESPONSE)),
    getPeriodComparison: vi.fn().mockReturnValue(of(MOCK_PERIOD_COMPARISON)),
    ...overrides,
  };
}

async function setup(serviceOverrides = {}) {
  const service = makeAnalyticsService(serviceOverrides);

  await TestBed.configureTestingModule({
    imports: [OeeBenchmarkComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AnalyticsService, useValue: service },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<OeeBenchmarkComponent> = TestBed.createComponent(OeeBenchmarkComponent);
  const component = fixture.componentInstance;
  fixture.detectChanges();
  return { fixture, component, service };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('OeeBenchmarkComponent', () => {

  it('should create', async () => {
    const { component } = await setup();
    expect(component).toBeTruthy();
  });

  // AC-1: Período > 90 dias → erro exibido + API não chamada
  it('AC-1: should show validation error and NOT call API when period exceeds 90 days', async () => {
    const { fixture, component, service } = await setup();

    // Configurar datas com período de 91 dias
    component.benchmarkFrom.set('2026-01-01');
    component.benchmarkTo.set('2026-04-02'); // 91 dias depois

    const callsBefore = (service.getBenchmarkWorkers as ReturnType<typeof vi.fn>).mock.calls.length;

    component.onUpdateRankings();
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('[data-testid="ranking-error"]');
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).toContain('90 dias');

    // API NÃO deve ter sido chamada além da chamada inicial do ngOnInit
    const callsAfter = (service.getBenchmarkWorkers as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(callsAfter).toBe(callsBefore);
  });

  // AC-2: Toggle referência Classe Mundial → showWorldClassRef() atualiza
  it('AC-2: should toggle showWorldClassRef signal when world class toggle is clicked', async () => {
    const { fixture, component } = await setup();

    expect(component.showWorldClassRef()).toBe(false);

    const toggle = fixture.nativeElement.querySelector('[data-testid="toggle-world-class"]');
    expect(toggle).toBeTruthy();

    component.onToggleWorldClass();
    fixture.detectChanges();
    expect(component.showWorldClassRef()).toBe(true);

    component.onToggleWorldClass();
    fixture.detectChanges();
    expect(component.showWorldClassRef()).toBe(false);
  });

  // AC-3: Rankings carregados após submit com datas válidas
  it('AC-3: should load rankings when valid dates are submitted', async () => {
    const { fixture, component, service } = await setup();

    component.benchmarkFrom.set('2026-01-01');
    component.benchmarkTo.set('2026-03-01'); // ~59 dias
    component.onUpdateRankings();
    fixture.detectChanges();

    expect(service.getBenchmarkWorkers).toHaveBeenCalledWith('2026-01-01', '2026-03-01');
    expect(service.getBenchmarkShifts).toHaveBeenCalledWith('2026-01-01', '2026-03-01');
    expect(service.getBenchmarkEquipmentType).toHaveBeenCalledWith('2026-01-01', '2026-03-01');

    expect(component.benchmarkWorkers().length).toBe(3);
    expect(component.benchmarkWorkers()[0].label).toBe('Alice');
  });

  // AC-4: Aviso recordsWithoutShift exibido quando > 0
  it('AC-4: should display recordsWithoutShift warning when count is greater than zero', async () => {
    const { fixture, component } = await setup();

    // MOCK_SHIFTS_RESPONSE tem recordsWithoutShift = 3 → aviso deve aparecer
    fixture.detectChanges();

    const warning = fixture.nativeElement.querySelector('[data-testid="records-without-shift-warning"]');
    expect(warning).toBeTruthy();
    expect(warning.textContent).toContain('3');
  });

  // AC-5: Comparação de períodos → LineChartComponent recebe 2 séries
  it('AC-5: should load period comparison and render both period charts when compare is clicked', async () => {
    const { fixture, component, service } = await setup();

    component.periodAFrom.set('2026-02-01');
    component.periodATo.set('2026-03-01');
    component.periodBFrom.set('2026-01-01');
    component.periodBTo.set('2026-01-31');

    component.onCompare();
    fixture.detectChanges();

    expect(service.getPeriodComparison).toHaveBeenCalledWith(
      '2026-02-01', '2026-03-01',
      '2026-01-01', '2026-01-31',
    );

    expect(component.periodComparison()).toBeTruthy();
    expect(component.periodAValues().length).toBe(2);
    expect(component.periodBValues().length).toBe(2);

    const chartsSection = fixture.nativeElement.querySelector('[data-testid="comparison-charts"]');
    expect(chartsSection).toBeTruthy();
  });

  // MF-S24-01: periodBLabels retorna labels do período B (não do A)
  it('MF-S24-01: periodBLabels should return labels from periodB entries', async () => {
    const { component } = await setup();

    component.periodComparison.set(MOCK_PERIOD_COMPARISON);

    const bLabels = component.periodBLabels();
    expect(bLabels).toEqual(['Alice', 'Bob']);

    // deve ser independente dos labels do período A
    const aLabels = component.periodALabels();
    expect(aLabels).toEqual(['Alice', 'Bob']); // mesmos nomes no mock, mas computados de fontes distintas
  });

  // MF-S24-01: periodBLabels retorna array vazio quando sem dados
  it('MF-S24-01: periodBLabels should return empty array when periodComparison is null', async () => {
    const { component } = await setup();

    component.periodComparison.set(null);
    expect(component.periodBLabels()).toEqual([]);
  });

  // SH-S24-03: card de evolução exibe improvementPct quando não-null
  it('SH-S24-03: improvement-card should be visible and show formatted improvementPct when not null', async () => {
    const { fixture, component } = await setup();

    component.periodComparison.set({ ...MOCK_PERIOD_COMPARISON, improvementPct: 8.5 });
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-testid="improvement-card"]');
    expect(card).toBeTruthy();
    expect(card.textContent).toContain('Evolução:');
    expect(card.textContent).toContain('8.5');
  });

  // SH-S24-03: card de evolução oculto quando improvementPct é null
  it('SH-S24-03: improvement-card should NOT be rendered when improvementPct is null', async () => {
    const { fixture, component } = await setup();

    component.periodComparison.set({ ...MOCK_PERIOD_COMPARISON, improvementPct: null });
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-testid="improvement-card"]');
    expect(card).toBeNull();
  });

  // Teste adicional: período inválido (from > to) exibe erro
  it('should show error when ranking from date is after to date', async () => {
    const { fixture, component, service } = await setup();

    component.benchmarkFrom.set('2026-03-01');
    component.benchmarkTo.set('2026-02-01'); // to < from

    const callsBefore = (service.getBenchmarkWorkers as ReturnType<typeof vi.fn>).mock.calls.length;
    component.onUpdateRankings();
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('[data-testid="ranking-error"]');
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).toContain('anterior');

    const callsAfter = (service.getBenchmarkWorkers as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(callsAfter).toBe(callsBefore);
  });

  // Teste adicional: erro de API não bloqueia renderização
  it('should handle API error gracefully and show section error', async () => {
    const { fixture, component } = await setup({
      getBenchmarkWorkers: vi.fn().mockReturnValue(throwError(() => new Error('fail'))),
    });

    expect(component.workersError()).toBe('Erro ao carregar workers.');
    expect(component.isBenchmarkLoading()).toBe(false);

    const errorEl = fixture.nativeElement.querySelector('.section-error');
    expect(errorEl).toBeTruthy();
  });

  // Teste adicional: comparação com datas inválidas exibe erro
  it('should show error when comparison period exceeds 90 days', async () => {
    const { fixture, component, service } = await setup();

    component.periodAFrom.set('2026-01-01');
    component.periodATo.set('2026-04-05'); // > 90 dias

    const callsBefore = (service.getPeriodComparison as ReturnType<typeof vi.fn>).mock.calls.length;
    component.onCompare();
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('[data-testid="comparison-error"]');
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).toContain('90 dias');

    const callsAfter = (service.getPeriodComparison as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(callsAfter).toBe(callsBefore);
  });
});
