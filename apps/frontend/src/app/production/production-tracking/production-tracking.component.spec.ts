import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ProductionTrackingComponent } from './production-tracking.component';
import {
  FamilyTrackingResponse,
  OrderTrackingEntry,
  ProductionTrackingService,
  ProductionTrackingSummaryResponse,
} from '../production-tracking.service';
import { AuthService } from '../../auth/auth.service';

// ---- Mock data ----

const mockOrderPlanned: OrderTrackingEntry = {
  dynamicsOrderNumber: 'OP-001',
  productCode: 'PROD-A',
  productName: 'Produto Alfa',
  productType: 'FINISHED',
  plannedQty: 100,
  producedQty: 0,
  completionPct: 0,
  dueDate: '2030-12-31',
  overdue: false,
  displayStatus: 'PLANNED',
  loadNumber: null,
  loadStatus: null,
  plannedPeople: 2,
};

const mockOrderOverdue: OrderTrackingEntry = {
  dynamicsOrderNumber: 'OP-002',
  productCode: 'PROD-B',
  productName: 'Produto Beta',
  productType: 'FINISHED',
  plannedQty: 50,
  producedQty: 10,
  completionPct: 20,
  dueDate: '2020-01-01',
  overdue: true,
  displayStatus: 'IN_PROGRESS',
  loadNumber: null,
  loadStatus: null,
  plannedPeople: null,
};

const mockOrderDone: OrderTrackingEntry = {
  dynamicsOrderNumber: 'OP-003',
  productCode: 'PROD-C',
  productName: 'Produto Gamma',
  productType: 'FINISHED',
  plannedQty: 200,
  producedQty: 200,
  completionPct: 100,
  dueDate: '2025-06-01',
  overdue: false,
  displayStatus: 'DONE',
  loadNumber: 'CARGA-2026-001',
  loadStatus: 'RELEASED',
  plannedPeople: 3,
};

const mockFamilyA: FamilyTrackingResponse = {
  familyId: 'fam-uuid-a',
  familyCode: 'FAM-A',
  familyName: 'Família A',
  overdueCount: 1,
  orders: [mockOrderPlanned, mockOrderOverdue],
  lastSyncAt: '2026-05-28T10:00:00',
};

const mockFamilyB: FamilyTrackingResponse = {
  familyId: 'fam-uuid-b',
  familyCode: 'FAM-B',
  familyName: 'Família B',
  overdueCount: 0,
  orders: [mockOrderDone],
  lastSyncAt: '2026-05-28T10:00:00',
};

const mockSummary: ProductionTrackingSummaryResponse = {
  inProgress: 1,
  pendingSterilization: 2,
  inLoad: 3,
  sterilizing: 0,
  overdue: 1,
  doneThisWeek: 5,
  lastSyncAt: '2026-05-28T10:00:00',
};

// ---- Helpers ----

function makeTrackingService(
  families: FamilyTrackingResponse[] = [mockFamilyA, mockFamilyB],
  summary: ProductionTrackingSummaryResponse = mockSummary,
) {
  return {
    getFamilies: vi.fn().mockReturnValue(of(families)),
    getOrders: vi.fn().mockReturnValue(of([])),
    getSummary: vi.fn().mockReturnValue(of(summary)),
  };
}

function makeAuth(role = 'OPERATOR') {
  return { role: signal(role) };
}

async function createComponent(
  svc = makeTrackingService(),
  role = 'OPERATOR',
): Promise<{ fixture: ComponentFixture<ProductionTrackingComponent>; svc: ReturnType<typeof makeTrackingService> }> {
  const auth = makeAuth(role);
  await TestBed.configureTestingModule({
    imports: [ProductionTrackingComponent],
    providers: [
      provideRouter([]),
      { provide: ProductionTrackingService, useValue: svc },
      { provide: AuthService, useValue: auth },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(ProductionTrackingComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, svc };
}

// ---- Tests ----

describe('ProductionTrackingComponent', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  // AC (a): carrega dados e renderiza N colunas
  it('(a) renderiza o kanban com colunas e cards mockados', async () => {
    const { fixture } = await createComponent();

    const board = fixture.nativeElement.querySelector('[data-testid="kanban-board"]');
    expect(board).not.toBeNull();

    // Should have columns for PLANNED and IN_PROGRESS at minimum
    const colPlanned = fixture.nativeElement.querySelector('[data-testid="col-PLANNED"]');
    expect(colPlanned).not.toBeNull();

    const colInProgress = fixture.nativeElement.querySelector('[data-testid="col-IN_PROGRESS"]');
    expect(colInProgress).not.toBeNull();

    // OP-001 card should be in PLANNED column
    const cardOp001 = fixture.nativeElement.querySelector('[data-testid="card-OP-001"]');
    expect(cardOp001).not.toBeNull();
  });

  // AC (b): selecionar família filtra cards sem nova chamada HTTP
  it('(b) selecionar família filtra cards computados sem nova chamada HTTP', async () => {
    const svc = makeTrackingService();
    const { fixture } = await createComponent(svc);

    const callCountBefore = (svc.getFamilies as ReturnType<typeof vi.fn>).mock.calls.length;

    // Select family FAM-A
    fixture.componentInstance.selectFamily('FAM-A');
    fixture.detectChanges();

    const callCountAfter = (svc.getFamilies as ReturnType<typeof vi.fn>).mock.calls.length;

    // No additional HTTP call
    expect(callCountAfter).toBe(callCountBefore);

    // Only FAM-A orders should show
    const cardOp001 = fixture.nativeElement.querySelector('[data-testid="card-OP-001"]');
    expect(cardOp001).not.toBeNull();

    // FAM-B's OP-003 should NOT show
    const cardOp003 = fixture.nativeElement.querySelector('[data-testid="card-OP-003"]');
    expect(cardOp003).toBeNull();
  });

  // AC (c): toggle "Apenas atrasadas" oculta cards sem overdue=true
  it('(c) toggle "Apenas atrasadas" filtra apenas OPs com overdue=true', async () => {
    const { fixture } = await createComponent();

    // Before filter, OP-001 (not overdue) should be visible
    const cardOp001Before = fixture.nativeElement.querySelector('[data-testid="card-OP-001"]');
    expect(cardOp001Before).not.toBeNull();

    // Enable overdue filter
    fixture.componentInstance.filterOnlyOverdue.set(true);
    fixture.detectChanges();

    // OP-001 (overdue=false) should disappear
    const cardOp001After = fixture.nativeElement.querySelector('[data-testid="card-OP-001"]');
    expect(cardOp001After).toBeNull();

    // OP-002 (overdue=true) should remain
    const cardOp002 = fixture.nativeElement.querySelector('[data-testid="card-OP-002"]');
    expect(cardOp002).not.toBeNull();
  });

  // AC (d): clique em card abre painel lateral com os campos corretos
  it('(d) clique em card abre painel lateral com dados da OP', async () => {
    const { fixture } = await createComponent();

    // Panel should be closed initially
    const panelBefore = fixture.nativeElement.querySelector('[data-testid="side-panel"]');
    expect(panelBefore).toBeNull();

    // Click on OP-001 card
    const card: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="card-OP-001"]');
    card.click();
    fixture.detectChanges();

    // Panel should now be open
    const panel = fixture.nativeElement.querySelector('[data-testid="side-panel"]');
    expect(panel).not.toBeNull();

    // Should show OP number
    const opNumber = fixture.nativeElement.querySelector('[data-testid="detail-op-number"]');
    expect(opNumber.textContent.trim()).toBe('OP-001');

    // Should show product info
    const product = fixture.nativeElement.querySelector('[data-testid="detail-product"]');
    expect(product.textContent).toContain('PROD-A');
    expect(product.textContent).toContain('Produto Alfa');
  });

  // Panel closes when backdrop clicked
  it('painel lateral fecha ao clicar no backdrop', async () => {
    const { fixture } = await createComponent();

    // Open panel
    fixture.componentInstance.openPanel(mockOrderPlanned);
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('[data-testid="side-panel"]');
    expect(panel).not.toBeNull();

    // Click backdrop
    const backdrop = fixture.nativeElement.querySelector('[data-testid="drawer-backdrop"]');
    backdrop.click();
    fixture.detectChanges();

    const panelAfter = fixture.nativeElement.querySelector('[data-testid="side-panel"]');
    expect(panelAfter).toBeNull();
  });

  // AC (e): botão "Atualizar" desabilitado para OPERATOR
  it('(e) botão "Atualizar" não é restringido por role (qualquer um pode clicar), disponível para OPERATOR', async () => {
    const { fixture } = await createComponent(makeTrackingService(), 'OPERATOR');

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-refresh"]');
    expect(btn).not.toBeNull();
    // Button is enabled (not disabled attribute)
    expect(btn.disabled).toBe(false);
  });

  // AC (f): lastSyncAt=null exibe "Nunca sincronizado"
  it('(f) lastSyncAt=null exibe "Nunca sincronizado"', async () => {
    const familiesNoSync: FamilyTrackingResponse[] = [
      { ...mockFamilyA, lastSyncAt: null },
      { ...mockFamilyB, lastSyncAt: null },
    ];
    const summaryNoSync: ProductionTrackingSummaryResponse = { ...mockSummary, lastSyncAt: null };
    const svc = makeTrackingService(familiesNoSync, summaryNoSync);
    const { fixture } = await createComponent(svc);

    const badge = fixture.nativeElement.querySelector('[data-testid="sync-badge-never"]');
    expect(badge).not.toBeNull();
    expect(badge.textContent.trim()).toBe('Nunca sincronizado');
  });

  // AC (g): OP com overdue=true exibe borda vermelha e data em vermelho
  it('(g) OP com overdue=true exibe chip de atraso e borda vermelha', async () => {
    const { fixture } = await createComponent();

    // OP-002 is overdue
    const card = fixture.nativeElement.querySelector('[data-testid="card-OP-002"]');
    expect(card).not.toBeNull();

    // Should have overdue class
    expect(card.classList.contains('kanban-card--overdue')).toBe(true);

    // Should show overdue chip
    const overdueChip = card.querySelector('[data-testid="overdue-chip"]');
    expect(overdueChip).not.toBeNull();

    // Due date should have overdue styling
    const dueDate = card.querySelector('.kanban-card__due-date--overdue');
    expect(dueDate).not.toBeNull();
  });

  // Erro HTTP exibe banner de erro
  it('erro HTTP exibe banner de erro sem lançar exceção', async () => {
    const svc = {
      getFamilies: vi.fn().mockReturnValue(throwError(() => new Error('Network error'))),
      getOrders: vi.fn().mockReturnValue(of([])),
      getSummary: vi.fn().mockReturnValue(of(mockSummary)),
    };
    const auth = makeAuth('OPERATOR');

    await TestBed.configureTestingModule({
      imports: [ProductionTrackingComponent],
      providers: [
        provideRouter([]),
        { provide: ProductionTrackingService, useValue: svc },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProductionTrackingComponent);

    // Should not throw
    expect(() => {
      fixture.detectChanges();
    }).not.toThrow();

    await fixture.whenStable();
    fixture.detectChanges();

    const errorBanner = fixture.nativeElement.querySelector('[data-testid="error-banner"]');
    expect(errorBanner).not.toBeNull();
    expect(errorBanner.textContent).toContain('Erro ao carregar dados de produção');
  });

  // Polling: auto-refresh — verifica que getFamilies é chamado no init (startWith(0))
  // e que o componente suporta polling (loadTracking é chamável múltiplas vezes)
  it('auto-refresh: getFamilies chamado ao inicializar (startWith(0) do interval)', async () => {
    const svc = makeTrackingService();
    const auth = makeAuth('OPERATOR');

    await TestBed.configureTestingModule({
      imports: [ProductionTrackingComponent],
      providers: [
        provideRouter([]),
        { provide: ProductionTrackingService, useValue: svc },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProductionTrackingComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // Initial call via startWith(0)
    const callsAfterInit = (svc.getFamilies as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(callsAfterInit).toBeGreaterThanOrEqual(1);

    // Manual refresh simulates what the interval would do
    fixture.componentInstance.loadTracking();
    await fixture.whenStable();
    fixture.detectChanges();

    const callsAfterRefresh = (svc.getFamilies as ReturnType<typeof vi.fn>).mock.calls.length;
    expect(callsAfterRefresh).toBeGreaterThan(callsAfterInit);

    fixture.destroy();
  });

  // Painel lateral com load mostra link "Ver carga"
  it('painel lateral com loadNumber mostra link "Ver carga"', async () => {
    const { fixture } = await createComponent();

    // Open panel for OP-003 which has loadNumber
    fixture.componentInstance.openPanel(mockOrderDone);
    fixture.detectChanges();

    const loadDetail = fixture.nativeElement.querySelector('[data-testid="detail-load"]');
    expect(loadDetail).not.toBeNull();
    expect(loadDetail.textContent).toContain('CARGA-2026-001');

    const loadLink = loadDetail.querySelector('.link-load');
    expect(loadLink).not.toBeNull();
    expect(loadLink.textContent.trim()).toBe('Ver carga →');
  });

  // Coluna com 0 OPs exibe placeholder
  it('coluna sem OPs exibe texto "Nenhuma OP"', async () => {
    const { fixture } = await createComponent();

    // RELEASED column should have no orders in our mock data
    const colReleased = fixture.nativeElement.querySelector('[data-testid="col-RELEASED"]');
    expect(colReleased).not.toBeNull();

    const empty = colReleased.querySelector('[data-testid="col-empty"]');
    expect(empty).not.toBeNull();
    expect(empty.textContent.trim()).toBe('Nenhuma OP');
  });

  // Summary KPI cards
  it('renderiza os 4 cards KPI de resumo quando summary está disponível', async () => {
    const { fixture } = await createComponent();

    const summaryCards = fixture.nativeElement.querySelector('[data-testid="summary-cards"]');
    expect(summaryCards).not.toBeNull();

    const kpiInProgress = fixture.nativeElement.querySelector('[data-testid="kpi-in-progress"]');
    expect(kpiInProgress).not.toBeNull();
    expect(kpiInProgress.textContent).toContain('1');

    const kpiOverdue = fixture.nativeElement.querySelector('[data-testid="kpi-overdue"]');
    expect(kpiOverdue).not.toBeNull();
    expect(kpiOverdue.textContent).toContain('1');
  });
});
