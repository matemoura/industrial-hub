import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { WorkOrderDetailComponent } from './work-order-detail.component';
import {
  MaintenanceService,
  WorkOrderPartResponse,
  WorkOrderResponse,
} from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';
import { AttachmentService } from '../../shared/attachment/attachment.service';

const WO: WorkOrderResponse = {
  id: 'wo-1',
  equipmentId: 'eq-1',
  equipmentCode: 'EQ-001',
  equipmentName: 'Torno CNC',
  type: 'CORRECTIVE',
  title: 'Troca de correia',
  description: null,
  priority: 'HIGH',
  status: 'OPEN',
  assignedTo: null,
  openedBy: 'op1',
  openedAt: '2026-05-21T08:00:00',
  startedAt: null,
  closedAt: null,
  scheduleId: null,
  shiftId: null,
  shiftName: null,
};

const PART1: WorkOrderPartResponse = {
  id: 'wop-1',
  sparePartId: 'sp-1',
  sparePartCode: 'PART-001',
  sparePartName: 'Correia',
  quantity: 2,
  addedBy: 'supervisor1',
  addedAt: '2026-05-21T09:00:00',
};

const PART2: WorkOrderPartResponse = {
  id: 'wop-2',
  sparePartId: 'sp-2',
  sparePartCode: 'PART-002',
  sparePartName: 'Rolamento',
  quantity: 1,
  addedBy: 'supervisor1',
  addedAt: '2026-05-21T09:05:00',
};

function makeAuthService(role: string) {
  return { role: signal(role) };
}

function makeRoute(id = 'wo-1') {
  return { snapshot: { paramMap: { get: () => id } } };
}

describe('WorkOrderDetailComponent', () => {
  let fixture: ComponentFixture<WorkOrderDetailComponent>;
  let component: WorkOrderDetailComponent;

  function setup(
    role = 'OPERATOR',
    parts: WorkOrderPartResponse[] = [PART1, PART2],
    listWorkOrderParts?: ReturnType<typeof vi.fn>,
  ) {
    const listWOParts = listWorkOrderParts ?? vi.fn().mockReturnValue(of(parts));
    const service = {
      getWorkOrder: vi.fn().mockReturnValue(of(WO)),
      listWorkOrderParts: listWOParts,
      listSpareParts: vi.fn().mockReturnValue(of([])),
      addWorkOrderPart: vi.fn(),
      removeWorkOrderPart: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [WorkOrderDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MaintenanceService, useValue: service },
        { provide: AuthService, useValue: makeAuthService(role) },
        { provide: ActivatedRoute, useValue: makeRoute('wo-1') },
        {
          provide: AttachmentService,
          useValue: {
            list: vi.fn().mockReturnValue(of([])),
            upload: vi.fn(),
            getDownloadUrl: vi.fn().mockReturnValue(of({ url: '' })),
            delete: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkOrderDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    return service;
  }

  // AC#1 — tabela exibe 2 peças
  it('should render 2 part rows', () => {
    setup();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid="part-row"]');
    expect(rows.length).toBe(2);
  });

  // AC#2 — estado vazio quando não há peças
  it('should show empty state when parts list is empty', () => {
    setup('OPERATOR', []);
    const empty = fixture.nativeElement.querySelector('[data-testid="parts-empty"]');
    expect(empty).toBeTruthy();
  });

  // AC#3 — erro 422 (estoque insuficiente) exibe snackbar
  it('should show 422 snackbar on insufficient stock', () => {
    const service = setup('SUPERVISOR');
    (service.addWorkOrderPart as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({ status: 422, error: { message: 'Estoque insuficiente.' } })),
    );

    component.openAddForm();
    component.addForm.setValue({ sparePartId: 'sp-1', quantity: 99 });
    component.submitAddPart();
    fixture.detectChanges();

    const snackbar = fixture.nativeElement.querySelector('[data-testid="snackbar"]');
    expect(snackbar).toBeTruthy();
    expect(snackbar.textContent).toContain('Estoque insuficiente.');
  });

  // AC#4 — botão Adicionar Peça oculto para OPERATOR
  it('should hide btn-add-part for OPERATOR', () => {
    setup('OPERATOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-add-part"]');
    expect(btn).toBeFalsy();
  });

  // AC#4 — botão Adicionar Peça visível para SUPERVISOR
  it('should show btn-add-part for SUPERVISOR', () => {
    setup('SUPERVISOR');
    const btn = fixture.nativeElement.querySelector('[data-testid="btn-add-part"]');
    expect(btn).toBeTruthy();
  });

  // AC#5 — clique no lixo abre diálogo de confirmação
  it('should open confirm dialog when trash button is clicked', () => {
    setup('SUPERVISOR');
    const trashBtn = fixture.nativeElement.querySelector('[data-testid="btn-remove-part"]');
    expect(trashBtn).toBeTruthy();
    trashBtn.click();
    fixture.detectChanges();

    const confirmBtn = fixture.nativeElement.querySelector('[data-testid="btn-confirm-remove"]');
    expect(confirmBtn).toBeTruthy();
  });
});
