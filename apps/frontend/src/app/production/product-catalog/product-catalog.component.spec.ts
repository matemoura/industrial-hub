import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ProductCatalogComponent } from './product-catalog.component';
import { ProductionService } from '../production.service';
import { AuthService } from '../../auth/auth.service';
import { signal } from '@angular/core';

const mockFamilies = [
  { id: 'fam-1', dynamicsCode: 'F001', name: 'Família A', active: true },
];

const mockProducts = [
  {
    id: 'prod-1',
    dynamicsCode: 'P001',
    name: 'Produto A',
    description: '',
    family: mockFamilies[0],
    active: true,
  },
  {
    id: 'prod-2',
    dynamicsCode: 'P002',
    name: 'Produto B',
    description: '',
    family: mockFamilies[0],
    active: false,
  },
];

const mockPage = { content: mockProducts, totalElements: 2, totalPages: 1, number: 0 };

function makeService() {
  return {
    listFamilies: vi.fn().mockReturnValue(of(mockFamilies)),
    listProducts: vi.fn().mockReturnValue(of(mockPage)),
    importProducts: vi.fn(),
  };
}

function makeAuth(role = 'SUPERVISOR') {
  return { role: signal(role) };
}

async function createComponent(role = 'SUPERVISOR') {
  const svc = makeService();
  const auth = makeAuth(role);
  await TestBed.configureTestingModule({
    imports: [ProductCatalogComponent],
    providers: [
      { provide: ProductionService, useValue: svc },
      { provide: AuthService, useValue: auth },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(ProductCatalogComponent);
  fixture.detectChanges();
  return { fixture, svc, auth };
}

describe('ProductCatalogComponent', () => {
  it('renders table with mocked products', async () => {
    const { fixture } = await createComponent();
    await fixture.whenStable();
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="product-row"]');
    expect(rows.length).toBe(2);

    const codes = fixture.nativeElement.querySelectorAll('[data-testid="product-code"]');
    expect(codes[0].textContent.trim()).toBe('P001');
    expect(codes[1].textContent.trim()).toBe('P002');
  });

  it('shows import button for SUPERVISOR', async () => {
    const { fixture } = await createComponent('SUPERVISOR');
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-excel"]');
    expect(btn).not.toBeNull();
  });

  it('hides import button for OPERATOR', async () => {
    const { fixture } = await createComponent('OPERATOR');
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-excel"]');
    expect(btn).toBeNull();
  });

  it('shows import button for ADMIN', async () => {
    const { fixture } = await createComponent('ADMIN');
    await fixture.whenStable();
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-import-excel"]');
    expect(btn).not.toBeNull();
  });

  it('expands upload panel when clicking import button', async () => {
    const { fixture } = await createComponent('SUPERVISOR');
    await fixture.whenStable();
    fixture.detectChanges();

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-excel"]');
    btn.click();
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('[data-testid="upload-panel"]');
    expect(panel).not.toBeNull();
  });

  it('calls importProducts with selected file and shows result', async () => {
    const mockResult = { imported: 5, updated: 2, skipped: 0, errors: [] };
    const { fixture, svc } = await createComponent('SUPERVISOR');
    svc.importProducts.mockReturnValue(of(mockResult));
    await fixture.whenStable();
    fixture.detectChanges();

    // Open upload panel
    const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-excel"]');
    importBtn.click();
    fixture.detectChanges();

    // Set selected file on component
    const comp = fixture.componentInstance;
    comp.selectedFile.set(new File(['data'], 'test.xlsx'));
    fixture.detectChanges();

    // Click confirm
    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-upload"]');
    confirmBtn.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(svc.importProducts).toHaveBeenCalled();
    const result = fixture.nativeElement.querySelector('[data-testid="import-result"]');
    expect(result).not.toBeNull();
    const importedCard = fixture.nativeElement.querySelector('[data-testid="result-imported"]');
    expect(importedCard.textContent).toContain('5');
  });

  it('shows error alert when import fails', async () => {
    const { fixture, svc } = await createComponent('SUPERVISOR');
    svc.importProducts.mockReturnValue(throwError(() => ({ error: { message: 'Arquivo inválido' } })));
    await fixture.whenStable();
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    const importBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-import-excel"]');
    importBtn.click();
    fixture.detectChanges();

    comp.selectedFile.set(new File(['data'], 'test.xlsx'));
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-upload"]');
    confirmBtn.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('[data-testid="upload-error"]');
    expect(errorEl).not.toBeNull();
    expect(errorEl.textContent).toContain('Arquivo inválido');
  });

  it('shows empty state when no products', async () => {
    const svc = makeService();
    svc.listProducts.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0 }));
    const auth = makeAuth('OPERATOR');
    await TestBed.configureTestingModule({
      imports: [ProductCatalogComponent],
      providers: [
        { provide: ProductionService, useValue: svc },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProductCatalogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(empty).not.toBeNull();
  });
});
