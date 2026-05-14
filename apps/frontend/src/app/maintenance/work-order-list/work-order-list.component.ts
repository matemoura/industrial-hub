import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  MaintenanceService,
  PageResponse,
  WorkOrderPriority,
  WorkOrderResponse,
  WorkOrderStatus,
  WorkOrderType,
} from '../maintenance.service';

@Component({
  selector: 'app-work-order-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './work-order-list.component.html',
  styleUrl: './work-order-list.component.scss',
})
export class WorkOrderListComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly router = inject(Router);

  page = signal<PageResponse<WorkOrderResponse> | null>(null);
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  filterEquipmentId = signal('');
  filterType = signal<WorkOrderType | ''>('');
  filterStatus = signal<WorkOrderStatus | ''>('');
  filterPriority = signal<WorkOrderPriority | ''>('');

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
    this.loadList(0);
  }

  loadList(pageIndex: number): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    const filters = {
      equipmentId: this.filterEquipmentId().trim() || undefined,
      type: this.filterType() || undefined,
      status: this.filterStatus() || undefined,
      priority: this.filterPriority() || undefined,
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
    this.loadList(0);
  }

  goToPage(index: number): void {
    this.loadList(index);
  }

  openEquipment(equipmentId: string): void {
    this.router.navigate(['/maintenance/equipment', equipmentId]);
  }
}
