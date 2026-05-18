import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { SupplierListComponent } from './supplier-list.component';
import { AuthService } from '../../auth/auth.service';
import { SupplierResponse } from '../qms.service';

const SUPPLIER_ACTIVE: SupplierResponse = {
  id: 'sup-1',
  code: 'FORN001',
  name: 'Fornecedor Alfa',
  contactEmail: 'alfa@example.com',
  contactPhone: null,
  address: null,
  active: true,
  onboardedAt: null,
};

const SUPPLIER_INACTIVE: SupplierResponse = {
  id: 'sup-2',
  code: 'FORN002',
  name: 'Fornecedor Beta',
  contactEmail: null,
  contactPhone: null,
  address: null,
  active: false,
  onboardedAt: null,
};

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

describe('SupplierListComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'ADMIN') {
    TestBed.configureTestingModule({
      imports: [SupplierListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SupplierListComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = setup();
    httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('AC-10 — tabela com código, nome, email e status', () => {
    it('shows skeleton rows while loading', () => {
      const fixture = setup();
      expect(fixture.nativeElement.querySelector('.skeleton-row')).not.toBeNull();
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
    });

    it('renders one row per supplier after response', () => {
      const fixture = setup();
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([SUPPLIER_ACTIVE, SUPPLIER_INACTIVE]);
      fixture.detectChanges();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="supplier-row"]');
      expect(rows.length).toBe(2);
    });

    it('displays supplier code in first column', () => {
      const fixture = setup();
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([SUPPLIER_ACTIVE]);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.supplier-code').textContent.trim()).toBe('FORN001');
    });

    it('displays supplier name in second column', () => {
      const fixture = setup();
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([SUPPLIER_ACTIVE]);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.supplier-name').textContent.trim()).toBe('Fornecedor Alfa');
    });

    it('shows empty state when API returns empty list', () => {
      const fixture = setup();
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.empty-state')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="supplier-row"]')).toBeNull();
    });
  });

  describe('role-based visibility', () => {
    it('shows "Novo Fornecedor" button for ADMIN', () => {
      const fixture = setup('ADMIN');
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
      fixture.detectChanges();
      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('Novo Fornecedor');
    });

    it('hides "Novo Fornecedor" button for OPERATOR', () => {
      const fixture = setup('OPERATOR');
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain('Novo Fornecedor');
    });

    it('shows ranking link for SUPERVISOR', () => {
      const fixture = setup('SUPERVISOR');
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Ranking');
    });

    it('shows ranking link for ADMIN', () => {
      const fixture = setup('ADMIN');
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Ranking');
    });

    it('hides ranking link for OPERATOR', () => {
      const fixture = setup('OPERATOR');
      httpTesting.expectOne('/api/v1/qms/suppliers').flush([]);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain('Ranking');
    });
  });
});
