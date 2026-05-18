import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SupplierDetailComponent } from './supplier-detail.component';
import { AuthService } from '../../auth/auth.service';
import { SupplierResponse, SupplierQualityScore } from '../qms.service';

const MOCK_SUPPLIER: SupplierResponse = {
  id: 'sup-1',
  code: 'FORN001',
  name: 'Fornecedor Alfa',
  contactEmail: 'alfa@example.com',
  contactPhone: '(11) 99999-0000',
  address: 'Rua das Flores, 100',
  active: true,
  onboardedAt: '2025-01-15',
};

const MOCK_SCORE_GREEN: SupplierQualityScore = {
  supplierId: 'sup-1',
  supplierName: 'Fornecedor Alfa',
  totalNcs: 2,
  criticalNcs: 0,
  highNcs: 0,
  qualityScore: 90.0,
};

const MOCK_SCORE_AMBER: SupplierQualityScore = {
  supplierId: 'sup-1',
  supplierName: 'Fornecedor Alfa',
  totalNcs: 5,
  criticalNcs: 0,
  highNcs: 2,
  qualityScore: 68.0,
};

const MOCK_SCORE_RED: SupplierQualityScore = {
  supplierId: 'sup-1',
  supplierName: 'Fornecedor Alfa',
  totalNcs: 10,
  criticalNcs: 3,
  highNcs: 2,
  qualityScore: 42.5,
};

const MOCK_ROUTE = {
  snapshot: { paramMap: { get: (_: string) => 'sup-1' } },
};

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

describe('SupplierDetailComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR') {
    TestBed.configureTestingModule({
      imports: [SupplierDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: MOCK_ROUTE },
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SupplierDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  function flushSupplier(score = MOCK_SCORE_GREEN) {
    httpTesting.expectOne('/api/v1/qms/suppliers/sup-1').flush(MOCK_SUPPLIER);
    httpTesting.expectOne((req) => req.url.includes('/api/v1/qms/suppliers/sup-1/quality-score')).flush(score);
  }

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = setup();
    flushSupplier();
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('AC-10 / US-057 — supplier info display', () => {
    it('displays supplier name as heading', () => {
      const fixture = setup();
      flushSupplier();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Fornecedor Alfa');
    });

    it('displays supplier code', () => {
      const fixture = setup();
      flushSupplier();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('FORN001');
    });

    it('shows error message when supplier not found (404)', () => {
      const fixture = setup();
      httpTesting.expectOne('/api/v1/qms/suppliers/sup-1').flush(
        { message: 'Fornecedor não encontrado' },
        { status: 404, statusText: 'Not Found' },
      );
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Fornecedor não encontrado');
    });
  });

  describe('AC-5 / US-058 — score card visible for SUPERVISOR+', () => {
    it('shows score card for SUPERVISOR', () => {
      const fixture = setup('SUPERVISOR');
      flushSupplier();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="score-card"]')).not.toBeNull();
    });

    it('shows score card for ADMIN', () => {
      const fixture = setup('ADMIN');
      flushSupplier();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="score-card"]')).not.toBeNull();
    });

    it('hides score card for OPERATOR', () => {
      const fixture = setup('OPERATOR');
      httpTesting.expectOne('/api/v1/qms/suppliers/sup-1').flush(MOCK_SUPPLIER);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="score-card"]')).toBeNull();
    });
  });

  describe('AC-5 — score gauge color', () => {
    it('gauge is green when score >= 80', () => {
      const fixture = setup();
      flushSupplier(MOCK_SCORE_GREEN);
      fixture.detectChanges();
      const gauge = fixture.nativeElement.querySelector('[data-testid="score-gauge"]');
      expect(gauge.classList).toContain('score-gauge--green');
    });

    it('gauge is amber when score 60–79', () => {
      const fixture = setup();
      flushSupplier(MOCK_SCORE_AMBER);
      fixture.detectChanges();
      const gauge = fixture.nativeElement.querySelector('[data-testid="score-gauge"]');
      expect(gauge.classList).toContain('score-gauge--amber');
    });

    it('gauge is red when score < 60', () => {
      const fixture = setup();
      flushSupplier(MOCK_SCORE_RED);
      fixture.detectChanges();
      const gauge = fixture.nativeElement.querySelector('[data-testid="score-gauge"]');
      expect(gauge.classList).toContain('score-gauge--red');
    });
  });

  describe('AC-6 — period selector (30 / 90 / 180 days)', () => {
    it('period selector has 3 options', () => {
      const fixture = setup();
      flushSupplier();
      fixture.detectChanges();
      const buttons = fixture.nativeElement.querySelectorAll('.period-btn');
      expect(buttons.length).toBe(3);
    });

    it('clicking 30d button reloads score for 30 days', () => {
      const fixture = setup();
      flushSupplier();
      fixture.detectChanges();

      const btn30: HTMLButtonElement = fixture.nativeElement.querySelectorAll('.period-btn')[0];
      btn30.click();
      fixture.detectChanges();

      const req = httpTesting.expectOne(
        (r) => r.url.includes('/quality-score') && r.params.get('days') === '30',
      );
      expect(req.request.method).toBe('GET');
      req.flush(MOCK_SCORE_GREEN);
    });
  });

  describe('role-based actions', () => {
    it('shows deactivate button for ADMIN on active supplier', () => {
      const fixture = setup('ADMIN');
      flushSupplier();
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('.btn--danger');
      expect(btn).not.toBeNull();
      expect(btn.textContent.trim()).toContain('Desativar');
    });

    it('hides deactivate button for SUPERVISOR', () => {
      const fixture = setup('SUPERVISOR');
      flushSupplier();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.btn--danger')).toBeNull();
    });

    it('hides deactivate button for OPERATOR', () => {
      const fixture = setup('OPERATOR');
      httpTesting.expectOne('/api/v1/qms/suppliers/sup-1').flush(MOCK_SUPPLIER);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.btn--danger')).toBeNull();
    });
  });
});
