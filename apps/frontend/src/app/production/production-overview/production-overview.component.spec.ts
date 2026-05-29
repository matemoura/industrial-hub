import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ProductionOverviewComponent } from './production-overview.component';
import { EfficiencyChartComponent } from './efficiency-chart.component';
import { ProductionOverviewDto } from '../production.service';
import { AuthService } from '../../auth/auth.service';

const mockOverview: ProductionOverviewDto = {
  bomCoverage: { totalFinishedProducts: 10, withBom: 7, withoutBom: 3, coveragePct: 70.0 },
  mrpFulfillment: { totalSuggestions: 5, accepted: 3, rejected: 1, pending: 1, fulfillmentPct: 75.0 },
  efficiencyTrend: [
    { date: '2026-05-25', avgEfficiency: 82.5 },
    { date: '2026-05-30', avgEfficiency: 78.0 },
  ],
  opsByStatus: { PLANNED: 12, IN_PROGRESS: 5, DONE: 30, CANCELLED: 2 },
};

describe('ProductionOverviewComponent', () => {
  let fixture: ComponentFixture<ProductionOverviewComponent>;
  let component: ProductionOverviewComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductionOverviewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { role: signal('SUPERVISOR') } },
      ],
      // NO_ERRORS_SCHEMA: trata ngx-charts-line-chart como elemento desconhecido (sem renderizar)
    })
    // US-108: override EfficiencyChartComponent com stub — NgxCharts usa SVG/D3 incompatível com JSDOM
    .overrideComponent(EfficiencyChartComponent, {
      set: { template: '<div data-testid="efficiency-chart-stub"></div>' },
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProductionOverviewComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should render KPI cards with data after load', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(mockOverview);
    fixture.detectChanges();

    const grid = fixture.nativeElement.querySelector('[data-testid="kpi-grid"]');
    expect(grid).toBeTruthy();

    const bom = fixture.nativeElement.querySelector('[data-testid="card-bom-coverage"] .kpi-value');
    expect(bom?.textContent?.trim()).toBe('70.0%');

    const mrp = fixture.nativeElement.querySelector('[data-testid="card-mrp-fulfillment"] .kpi-value');
    expect(mrp?.textContent?.trim()).toBe('75.0%');
  });

  it('should display — for null coverage when no finished products', () => {
    const empty: ProductionOverviewDto = {
      ...mockOverview,
      bomCoverage: { totalFinishedProducts: 0, withBom: 0, withoutBom: 0, coveragePct: null },
    };
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(empty);
    fixture.detectChanges();

    const bom = fixture.nativeElement.querySelector('[data-testid="card-bom-coverage"] .kpi-value');
    expect(bom?.textContent?.trim()).toBe('—');
  });

  it('should render efficiency trend sr-only table with rows', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(mockOverview);
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('[data-testid="trend-table-sr"]');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
  });

  it('should show empty trend message when no data', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush({ ...mockOverview, efficiencyTrend: [] });
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('[data-testid="trend-empty"]');
    expect(empty).toBeTruthy();
  });

  it('should render OPs table sorted by count desc', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(mockOverview);
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('[data-testid="ops-table"]');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr');
    // DONE=30, PLANNED=12, IN_PROGRESS=5, CANCELLED=2 → 4 rows
    expect(rows.length).toBe(4);
    // primeira linha = maior count (DONE=30)
    const firstRowCells = rows[0].querySelectorAll('td');
    expect(firstRowCells[0]?.textContent?.trim()).toBe('Concluída');
  });

  it('should show error message on load failure', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const err = fixture.nativeElement.querySelector('[data-testid="error-msg"]');
    expect(err).toBeTruthy();
  });

  it('should show loading state while request is pending', () => {
    // Antes do flush: a requisição ainda está em andamento
    const loading = fixture.nativeElement.querySelector('[data-testid="loading"]');
    expect(loading).toBeTruthy();
    expect(loading.textContent?.trim()).toBe('Carregando painel...');

    // Limpa a requisição pendente para não falhar no afterEach verify()
    httpMock.expectOne((r) => r.url.includes('/overview')).flush(mockOverview);
  });

  // === US-108 — NgxCharts ===

  it('chartData() should transform trendRows into NgxCharts series format', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(mockOverview);
    fixture.detectChanges();

    const data = component.chartData();
    expect(data).toHaveLength(1);
    expect(data[0].name).toBe('Eficiência');
    expect(data[0].series).toHaveLength(2);
    expect(data[0].series[0].name).toBe('2026-05-25');
    expect(data[0].series[0].value).toBe(82.5);
    expect(data[0].series[1].name).toBe('2026-05-30');
    expect(data[0].series[1].value).toBe(78.0);
  });

  it('should render app-efficiency-chart when trend data is available', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(mockOverview);
    fixture.detectChanges();

    const chartContainer = fixture.nativeElement.querySelector('[data-testid="trend-chart"]');
    expect(chartContainer).toBeTruthy();
    // EfficiencyChartComponent renderizado (stub via overrideComponent)
    const chartStub = fixture.nativeElement.querySelector('[data-testid="efficiency-chart-stub"]');
    expect(chartStub).toBeTruthy();
  });

  it('should render sr-only accessibility table even when chart is active', () => {
    const req = httpMock.expectOne((r) => r.url.includes('/overview'));
    req.flush(mockOverview);
    fixture.detectChanges();

    // Gráfico presente (stub)
    const chartStub = fixture.nativeElement.querySelector('[data-testid="efficiency-chart-stub"]');
    expect(chartStub).toBeTruthy();

    // Tabela acessível também presente (WCAG AA)
    const srTable = fixture.nativeElement.querySelector('[data-testid="trend-table-sr"]');
    expect(srTable).toBeTruthy();
    expect(srTable.classList.contains('sr-only')).toBe(true);
  });
});
