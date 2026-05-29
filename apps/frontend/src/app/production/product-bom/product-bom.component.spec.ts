import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ProductBomComponent } from './product-bom.component';
import { BomComponentRow, ProductionService } from '../production.service';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../auth/auth.service';

const mockBom: BomComponentRow[] = [
  { componentCode: 'COMP-101', componentName: 'Componente A', quantity: 2, unit: 'UN', level: 1, productType: 'INTERMEDIATE' },
  { componentCode: 'RAW-201',  componentName: 'Matéria B',    quantity: 0.5, unit: 'KG', level: 1, productType: 'RAW_MATERIAL' },
];

describe('ProductBomComponent', () => {
  let fixture: ComponentFixture<ProductBomComponent>;
  let component: ProductBomComponent;
  let httpMock: HttpTestingController;

  function createComponent(role = 'ADMIN') {
    TestBed.configureTestingModule({
      imports: [ProductBomComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { role: signal(role) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductBomComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('productCode', 'PROD-001');
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock?.verify());

  it('should render BOM table with components', () => {
    createComponent();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.includes('/products/PROD-001/bom'));
    req.flush(mockBom);
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('[data-testid="bom-table"]');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
  });

  it('should show empty message when BOM is empty', () => {
    createComponent();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.includes('/products/PROD-001/bom'));
    req.flush([]);
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('[data-testid="bom-empty"]');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('BOM não cadastrado');
  });

  it('should apply indentation to level-2 rows', () => {
    const bom2: typeof mockBom = [
      ...mockBom,
      { componentCode: 'RAW-301', componentName: 'Sub-Matéria', quantity: 1.0, unit: 'UN', level: 2, productType: 'RAW_MATERIAL' },
    ];
    createComponent('SUPERVISOR');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.includes('/products/PROD-001/bom'));
    req.flush(bom2);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="bom-table"] tbody tr');
    expect(rows.length).toBe(3);
    // nível 2: última linha tem class level-2
    expect(rows[2].classList.contains('level-2')).toBe(true);
    // nível 1: primeira linha NÃO tem class level-2
    expect(rows[0].classList.contains('level-2')).toBe(false);
  });

  it('should show import button only for ADMIN role', () => {
    createComponent('SUPERVISOR');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.includes('/products/PROD-001/bom'));
    req.flush([]);
    fixture.detectChanges();

    const uploadInput = fixture.nativeElement.querySelector('[data-testid="bom-file-input"]');
    expect(uploadInput).toBeNull(); // SUPERVISOR não vê botão de importação
  });
});
