import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SupplierRankingComponent } from './supplier-ranking.component';
import { SupplierQualityScore } from '../qms.service';

const SCORE_RED: SupplierQualityScore = {
  supplierId: 'sup-1',
  supplierName: 'Fornecedor Ruim',
  totalNcs: 20,
  criticalNcs: 5,
  highNcs: 3,
  qualityScore: 30.0,
};

const SCORE_AMBER: SupplierQualityScore = {
  supplierId: 'sup-2',
  supplierName: 'Fornecedor Médio',
  totalNcs: 8,
  criticalNcs: 0,
  highNcs: 2,
  qualityScore: 68.0,
};

const SCORE_GREEN: SupplierQualityScore = {
  supplierId: 'sup-3',
  supplierName: 'Fornecedor Bom',
  totalNcs: 2,
  criticalNcs: 0,
  highNcs: 0,
  qualityScore: 95.0,
};

describe('SupplierRankingComponent', () => {
  let httpTesting: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      imports: [SupplierRankingComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SupplierRankingComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = setup();
    httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([]);
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('AC-7 — tabela de ranking com barra de progresso', () => {
    it('shows loading skeleton rows while fetching', () => {
      const fixture = setup();
      expect(fixture.nativeElement.querySelector('.skeleton-row')).not.toBeNull();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([]);
    });

    it('renders one row per supplier after response', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking'))
        .flush([SCORE_RED, SCORE_AMBER, SCORE_GREEN]);
      fixture.detectChanges();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="ranking-row"]');
      expect(rows.length).toBe(3);
    });

    it('shows empty state when ranking is empty', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.empty-state')).not.toBeNull();
    });

    it('shows score badge for each row', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking'))
        .flush([SCORE_RED, SCORE_GREEN]);
      fixture.detectChanges();
      const badges = fixture.nativeElement.querySelectorAll('[data-testid="score-badge"]');
      expect(badges.length).toBe(2);
    });

    it('score badge is red when score < 60', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([SCORE_RED]);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="score-badge"]');
      expect(badge.classList).toContain('score-badge--red');
    });

    it('score badge is amber when score 60–79', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([SCORE_AMBER]);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="score-badge"]');
      expect(badge.classList).toContain('score-badge--amber');
    });

    it('score badge is green when score >= 80', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([SCORE_GREEN]);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="score-badge"]');
      expect(badge.classList).toContain('score-badge--green');
    });
  });

  describe('period selector', () => {
    it('loads with 90 days by default', () => {
      const fixture = setup();
      const req = httpTesting.expectOne((r) => r.url.includes('/quality-ranking'));
      expect(req.request.params.get('days')).toBe('90');
      req.flush([]);
    });

    it('reloads when 30-day period is selected', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([]);
      fixture.detectChanges();

      const btn30: HTMLButtonElement = fixture.nativeElement.querySelectorAll('.period-btn')[0];
      btn30.click();
      fixture.detectChanges();

      const req = httpTesting.expectOne((r) => r.url.includes('/quality-ranking'));
      expect(req.request.params.get('days')).toBe('30');
      req.flush([]);
    });

    it('active period button has period-btn--active class', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking')).flush([]);
      fixture.detectChanges();

      // Default is 90 days — second button (index 1) should be active
      const buttons = fixture.nativeElement.querySelectorAll('.period-btn');
      expect(buttons[1].classList).toContain('period-btn--active');
    });

    it('shows error message on API failure', () => {
      const fixture = setup();
      httpTesting.expectOne((r) => r.url.includes('/quality-ranking'))
        .flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Erro ao carregar ranking');
    });
  });
});
