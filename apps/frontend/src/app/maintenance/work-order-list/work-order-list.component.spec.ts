import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { WorkOrderListComponent } from './work-order-list.component';
import { MaintenanceService, WorkOrderResponse, PageResponse } from '../maintenance.service';
import { AdminService, Shift } from '../../admin/admin.service';

const BASE_WO: WorkOrderResponse = {
  id: 'wo-1',
  equipmentId: 'eq-1',
  equipmentCode: 'EQ001',
  equipmentName: 'Torno CNC',
  type: 'CORRECTIVE',
  title: 'Falha no motor',
  description: null,
  priority: 'HIGH',
  status: 'OPEN',
  assignedTo: null,
  openedBy: 'operator',
  openedAt: '2026-05-01T08:00:00',
  startedAt: null,
  closedAt: null,
  scheduleId: null,
  shiftId: null,
  shiftName: null,
};

function makePageResponse(items: WorkOrderResponse[]): PageResponse<WorkOrderResponse> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

const MOCK_SHIFTS: Shift[] = [
  { id: 's1', name: 'Manhã', startTime: '06:00', endTime: '14:00', overnight: false, active: true },
  { id: 's2', name: 'Tarde', startTime: '14:00', endTime: '22:00', overnight: false, active: true },
];

function makeServices(woItems: WorkOrderResponse[] = [BASE_WO], shifts: Shift[] = []) {
  return {
    maintenanceService: {
      listWorkOrders: vi.fn().mockReturnValue(of(makePageResponse(woItems))),
      getWorkOrderMetrics: vi.fn().mockReturnValue(of({ mttr: null, totalOrders: 1, openOrders: 1 })),
    },
    adminService: {
      getShifts: vi.fn().mockReturnValue(of(shifts)),
    },
  };
}

async function createFixture(
  maintenanceService: unknown,
  adminService: unknown,
): Promise<ComponentFixture<WorkOrderListComponent>> {
  await TestBed.configureTestingModule({
    imports: [WorkOrderListComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MaintenanceService, useValue: maintenanceService },
      { provide: AdminService, useValue: adminService },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(WorkOrderListComponent);
  fixture.detectChanges();
  return fixture;
}

describe('WorkOrderListComponent', () => {

  // ─── US-055 (a): OS com shiftName="Manhã" exibe chip ──────────────────────
  it('(US-055-a) deve exibir chip de turno quando shiftName não é null', async () => {
    const wo = { ...BASE_WO, id: 'wo-shift', shiftId: 's1', shiftName: 'Manhã' };
    const { maintenanceService, adminService } = makeServices([wo]);

    const fixture = await createFixture(maintenanceService, adminService);

    const chip = fixture.nativeElement.querySelector('[data-testid="shift-chip-wo-shift"]');
    expect(chip).toBeTruthy();
    expect(chip.textContent.trim()).toBe('Manhã');
  });

  // ─── US-055 (b): OS com shiftName=null não exibe chip ─────────────────────
  it('(US-055-b) não deve exibir chip de turno quando shiftName é null', async () => {
    const wo = { ...BASE_WO, id: 'wo-noshift', shiftId: null, shiftName: null };
    const { maintenanceService, adminService } = makeServices([wo]);

    const fixture = await createFixture(maintenanceService, adminService);

    const chip = fixture.nativeElement.querySelector('[data-testid="shift-chip-wo-noshift"]');
    expect(chip).toBeFalsy();
  });

  // ─── US-056: dropdown exibido quando shifts() tem itens ───────────────────
  it('(US-056) deve exibir dropdown de turno quando shifts() tem itens', async () => {
    const { maintenanceService, adminService } = makeServices([BASE_WO], MOCK_SHIFTS);

    const fixture = await createFixture(maintenanceService, adminService);
    fixture.componentInstance.shifts.set(MOCK_SHIFTS);
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('[data-testid="shift-filter"]');
    expect(select).toBeTruthy();
  });

  // ─── US-056: dropdown oculto quando shifts() está vazio ───────────────────
  it('(US-056) deve ocultar dropdown de turno quando shifts() está vazio', async () => {
    const { maintenanceService, adminService } = makeServices([BASE_WO], []);

    const fixture = await createFixture(maintenanceService, adminService);

    const select = fixture.nativeElement.querySelector('[data-testid="shift-filter"]');
    expect(select).toBeFalsy();
  });

  // ─── US-056: seleção de turno chama serviço com shiftId ───────────────────
  it('(US-056) seleção de turno deve chamar listWorkOrders com shiftId', async () => {
    const { maintenanceService, adminService } = makeServices([BASE_WO], MOCK_SHIFTS);

    const fixture = await createFixture(maintenanceService, adminService);
    const component = fixture.componentInstance;
    component.shifts.set(MOCK_SHIFTS);
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('[data-testid="shift-filter"]');
    select.value = 's1';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const calls = (maintenanceService.listWorkOrders as ReturnType<typeof vi.fn>).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[0]?.shiftId).toBe('s1');
  });
});
