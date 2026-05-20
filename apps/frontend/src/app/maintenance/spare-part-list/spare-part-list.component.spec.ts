import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { SparePartListComponent } from './spare-part-list.component';
import { MaintenanceService, SparePartResponse } from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

const PART_OK: SparePartResponse = {
  id: 'p1',
  code: 'PART-001',
  name: 'Rolamento',
  category: 'Rolamentos',
  unit: 'un',
  stockQty: 10,
  minStockQty: 2,
  active: true,
  belowMin: false,
};

const PART_LOW: SparePartResponse = {
  id: 'p2',
  code: 'PART-002',
  name: 'Correia',
  category: 'Correias',
  unit: 'un',
  stockQty: 1,
  minStockQty: 5,
  active: true,
  belowMin: true,
};

const PART_3: SparePartResponse = {
  id: 'p3',
  code: 'PART-003',
  name: 'Filtro',
  category: null,
  unit: null,
  stockQty: 20,
  minStockQty: 3,
  active: true,
  belowMin: false,
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

describe('SparePartListComponent', () => {
  let fixture: ComponentFixture<SparePartListComponent>;
  let listSpareParts: ReturnType<typeof vi.fn>;

  function setup(role = 'OPERATOR', parts: SparePartResponse[] = [PART_OK, PART_LOW, PART_3]) {
    listSpareParts = vi.fn().mockReturnValue(of(parts));
    const service = {
      listSpareParts,
      createSparePart: vi.fn(),
      updateSparePart: vi.fn(),
      adjustStock: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [SparePartListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MaintenanceService, useValue: service },
        { provide: AuthService, useValue: makeAuthService(role) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SparePartListComponent);
    fixture.detectChanges();
  }

  // AC#1 — tabela exibe as 3 peças
  it('should render a row per part', () => {
    setup();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid="part-row"]');
    expect(rows.length).toBe(3);
  });

  // AC#2 — linha abaixo do mínimo recebe classe CSS part-row--below-min
  it('should apply below-min CSS class to rows with belowMin=true', () => {
    setup();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid="part-row"]');
    // PART_OK → no class; PART_LOW → has class
    expect(rows[0].classList.contains('part-row--below-min')).toBe(false);
    expect(rows[1].classList.contains('part-row--below-min')).toBe(true);
  });

  // AC#3 — estado vazio exibido quando lista está vazia
  it('should show empty state when no parts', () => {
    setup('OPERATOR', []);
    const empty = fixture.nativeElement.querySelector('[data-testid="empty"]');
    expect(empty).toBeTruthy();
  });

  // AC#4 — erro 409 exibe snackbar com "Código já existe."
  it('should show 409 conflict snackbar on duplicate code', async () => {
    const createFn = vi.fn().mockReturnValue(throwError(() => ({ status: 409 })));
    const service = {
      listSpareParts: vi.fn().mockReturnValue(of([])),
      createSparePart: createFn,
      updateSparePart: vi.fn(),
      adjustStock: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [SparePartListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MaintenanceService, useValue: service },
        { provide: AuthService, useValue: makeAuthService('ADMIN') },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SparePartListComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    // open create dialog and fill form
    component.openCreateDialog();
    component.form.setValue({ code: 'X', name: 'Test', category: '', unit: '', stockQty: 5, minStockQty: 1 });
    component.submitCreate();
    fixture.detectChanges();

    const snackbar = fixture.nativeElement.querySelector('[data-testid="snackbar"]');
    expect(snackbar).toBeTruthy();
    expect(snackbar.textContent).toContain('Código já existe.');
  });

  // AC#5 — botão Nova Peça oculto para OPERATOR
  it('should hide btn-new-part for OPERATOR', () => {
    setup('OPERATOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-part"]');
    expect(btn).toBeFalsy();
  });

  // AC#5 — botão Nova Peça visível para ADMIN
  it('should show btn-new-part for ADMIN', () => {
    setup('ADMIN');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-new-part"]');
    expect(btn).toBeTruthy();
  });
});
