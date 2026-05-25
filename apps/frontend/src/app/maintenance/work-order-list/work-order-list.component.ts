import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  MaintenanceService,
  PageResponse,
  WorkOrderMetricsResponse,
  WorkOrderPriority,
  WorkOrderResponse,
  WorkOrderStatus,
  WorkOrderType,
} from '../maintenance.service';
import { AdminService, Shift } from '../../admin/admin.service';
import { SlaBreachedChipComponent } from '../../shared/sla-breached-chip/sla-breached-chip.component';
import { PlantContextService } from '../../shared/plant-selector/plant-context.service';

@Component({
  selector: 'app-work-order-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DecimalPipe, SlaBreachedChipComponent],
  templateUrl: './work-order-list.component.html',
  styleUrl: './work-order-list.component.scss',
})
export class WorkOrderListComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly adminService = inject(AdminService);
  private readonly router = inject(Router);
  readonly plantContext = inject(PlantContextService);

  page = signal<PageResponse<WorkOrderResponse> | null>(null);
  loading = signal(false);
  errorMsg = signal<string | null>(null);
  readonly shiftsErrorMsg = signal<string>('');

  globalMetrics = signal<WorkOrderMetricsResponse | null>(null);

  // Shift filter
  shifts = signal<Shift[]>([]);
  selectedShiftId = signal<string | null>(null);

  filterEquipmentId = signal('');
  filterType = signal<WorkOrderType | ''>('');
  filterStatus = signal<WorkOrderStatus | ''>('');
  filterPriority = signal<WorkOrderPriority | ''>('');
  filterSlaBreached = signal(false);

  readonly woTypes: WorkOrderType[] = ['CORRECTIVE', 'PREVENTIVE'];
  readonly woStatuses: WorkOrderStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'];
  readonly woPriorities: WorkOrderPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

  readonly woTypeLabels: Record<WorkOrderType, string> = {
    CORRECTIVE: 'Corretiva',
    PREVENTIVE: 'Preventiva',
  };

  readonly woStatusLabels: Record<WorkOrderStatus, string> = {
    OPEN: 'Aberta',
    IN_PROGRESS: 'Em andamento',
    DONE: 'Concluída',
    CANCELLED: 'Cancelada',
  };

  readonly woPriorityLabels: Record<WorkOrderPriority, string> = {
    LOW: 'Baixa',
    MEDIUM: 'Média',
    HIGH: 'Alta',
    URGENT: 'Urgente',
  };

  ngOnInit(): void {
    this.loadShifts();
    this.loadList(0);
    this.loadGlobalMetrics();
  }

  loadShifts(): void {
    this.adminService.getShifts().subscribe({
      next: (list) => this.shifts.set(list),
      error: () => this.shiftsErrorMsg.set('Erro ao carregar turnos.'),
    });
  }

  onShiftChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedShiftId.set(value || null);
    this.loadList(0);
  }

  loadGlobalMetrics(): void {
    this.maintenanceService.getWorkOrderMetrics().subscribe({
      next: (m) => this.globalMetrics.set(m),
      error: () => { /* métricas globais não bloqueiam a página */ },
    });
  }

  loadList(pageIndex: number): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    const filters = {
      equipmentId: this.filterEquipmentId().trim() || undefined,
      type: this.filterType() || undefined,
      status: this.filterStatus() || undefined,
      priority: this.filterPriority() || undefined,
      shiftId: this.selectedShiftId() ?? undefined,
      slaBreached: this.filterSlaBreached() ? true : undefined,
    };
    this.maintenanceService.listWorkOrders(filters, pageIndex).subscribe({
      next: (p) => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar ordens de serviço.');
        this.loading.set(false);
      },
    });
  }

  applyFilters(): void {
    this.loadList(0);
  }

  clearFilters(): void {
    this.filterEquipmentId.set('');
    this.filterType.set('');
    this.filterStatus.set('');
    this.filterPriority.set('');
    this.selectedShiftId.set(null);
    this.filterSlaBreached.set(false);
    this.loadList(0);
  }

  goToPage(index: number): void {
    this.loadList(index);
  }

  openEquipment(equipmentId: string): void {
    this.router.navigate(['/maintenance/equipment', equipmentId]);
  }
}
