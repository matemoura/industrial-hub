import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  EquipmentResponse,
  EquipmentStatus,
  EquipmentType,
  MaintenanceService,
  WorkOrderPriority,
  WorkOrderResponse,
  WorkOrderStatus,
  WorkOrderType,
} from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-equipment-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule],
  templateUrl: './equipment-detail.component.html',
  styleUrl: './equipment-detail.component.scss',
})
export class EquipmentDetailComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly role = this.authService.role;

  equipment = signal<EquipmentResponse | null>(null);
  loading = signal(true);
  errorMsg = signal<string | null>(null);

  workOrders = signal<WorkOrderResponse[]>([]);
  workOrdersLoading = signal(false);

  showWorkOrderForm = signal(false);
  woType = signal<WorkOrderType | ''>('');
  woTitle = signal('');
  woPriority = signal<WorkOrderPriority | ''>('');
  woDescription = signal('');
  woAssignedTo = signal('');
  woSubmitting = signal(false);
  woError = signal<string | null>(null);

  readonly equipmentTypeLabels: Record<EquipmentType, string> = {
    MACHINE: 'Máquina',
    TOOL: 'Ferramenta',
    VEHICLE: 'Veículo',
    INFRASTRUCTURE: 'Infraestrutura',
  };

  readonly equipmentStatusLabels: Record<EquipmentStatus, string> = {
    OPERATIONAL: 'Operacional',
    UNDER_MAINTENANCE: 'Em manutenção',
    DECOMMISSIONED: 'Desativado',
  };

  readonly woTypes: WorkOrderType[] = ['CORRECTIVE', 'PREVENTIVE'];
  readonly woPriorities: WorkOrderPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

  readonly woTypeLabels: Record<WorkOrderType, string> = {
    CORRECTIVE: 'Corretiva',
    PREVENTIVE: 'Preventiva',
  };

  readonly woPriorityLabels: Record<WorkOrderPriority, string> = {
    LOW: 'Baixa',
    MEDIUM: 'Média',
    HIGH: 'Alta',
    URGENT: 'Urgente',
  };

  readonly woStatusLabels: Record<WorkOrderStatus, string> = {
    OPEN: 'Aberta',
    IN_PROGRESS: 'Em andamento',
    DONE: 'Concluída',
    CANCELLED: 'Cancelada',
  };

  get isAdmin(): boolean {
    return this.role() === 'ADMIN';
  }

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  get isWoFormValid(): boolean {
    return this.woType() !== '' && this.woTitle().trim().length > 0 && this.woPriority() !== '';
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadEquipment(id);
    this.loadWorkOrders(id);
  }

  loadEquipment(id: string): void {
    this.loading.set(true);
    this.maintenanceService.getEquipment(id).subscribe({
      next: (eq) => {
        this.equipment.set(eq);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Equipamento não encontrado.');
        this.loading.set(false);
      },
    });
  }

  loadWorkOrders(equipmentId: string): void {
    this.workOrdersLoading.set(true);
    this.maintenanceService.listWorkOrders({ equipmentId }).subscribe({
      next: (page) => {
        this.workOrders.set(page.content);
        this.workOrdersLoading.set(false);
      },
      error: () => {
        this.workOrdersLoading.set(false);
      },
    });
  }

  transition(workOrderId: string, status: WorkOrderStatus): void {
    const labelMap: Record<WorkOrderStatus, string> = {
      IN_PROGRESS: 'iniciar',
      DONE: 'concluir',
      CANCELLED: 'cancelar',
      OPEN: 'reabrir',
    };
    if (!confirm(`Confirma ${labelMap[status]} esta OS?`)) return;

    this.maintenanceService.transitionStatus(workOrderId, status).subscribe({
      next: (updated) => {
        this.workOrders.update((list) => list.map((wo) => (wo.id === workOrderId ? updated : wo)));
        const eq = this.equipment();
        if (eq) this.loadEquipment(eq.id);
      },
      error: (err) => {
        this.woError.set(err?.error?.message ?? 'Erro ao atualizar status.');
      },
    });
  }

  submitWorkOrder(): void {
    const eq = this.equipment();
    if (!eq || !this.isWoFormValid || this.woSubmitting()) return;

    this.woSubmitting.set(true);
    this.woError.set(null);

    this.maintenanceService
      .createWorkOrder({
        equipmentId: eq.id,
        type: this.woType() as WorkOrderType,
        title: this.woTitle().trim(),
        priority: this.woPriority() as WorkOrderPriority,
        description: this.woDescription().trim() || undefined,
        assignedTo: this.woAssignedTo().trim() || undefined,
      })
      .subscribe({
        next: (wo) => {
          this.workOrders.update((list) => [wo, ...list]);
          this.woType.set('');
          this.woTitle.set('');
          this.woPriority.set('');
          this.woDescription.set('');
          this.woAssignedTo.set('');
          this.showWorkOrderForm.set(false);
          this.woSubmitting.set(false);
          this.loadEquipment(eq.id);
        },
        error: (err) => {
          this.woError.set(err?.error?.message ?? 'Erro ao criar OS. Tente novamente.');
          this.woSubmitting.set(false);
        },
      });
  }

  deactivate(): void {
    const eq = this.equipment();
    if (!eq || !confirm(`Confirma a desativação do equipamento "${eq.name}"?`)) return;

    this.maintenanceService.deleteEquipment(eq.id).subscribe({
      next: () => {
        this.router.navigate(['/maintenance/equipment'], {
          state: { toast: 'Equipamento desativado com sucesso' },
        });
      },
      error: (err) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao desativar equipamento.');
      },
    });
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return iso.replace('T', ' ').slice(0, 16);
  }
}
