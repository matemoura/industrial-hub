import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  EquipmentResponse,
  EquipmentStatus,
  EquipmentType,
  MaintenanceService,
} from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-equipment-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './equipment-list.component.html',
  styleUrl: './equipment-list.component.scss',
})
export class EquipmentListComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = this.authService.role;

  equipment = signal<EquipmentResponse[]>([]);
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  filterType = signal<EquipmentType | ''>('');
  filterStatus = signal<EquipmentStatus | ''>('');

  readonly equipmentTypes: EquipmentType[] = ['MACHINE', 'TOOL', 'VEHICLE', 'INFRASTRUCTURE'];
  readonly equipmentStatuses: EquipmentStatus[] = ['OPERATIONAL', 'UNDER_MAINTENANCE', 'DECOMMISSIONED'];

  readonly typeLabels: Record<EquipmentType, string> = {
    MACHINE: 'Máquina',
    TOOL: 'Ferramenta',
    VEHICLE: 'Veículo',
    INFRASTRUCTURE: 'Infraestrutura',
  };

  readonly statusLabels: Record<EquipmentStatus, string> = {
    OPERATIONAL: 'Operacional',
    UNDER_MAINTENANCE: 'Em manutenção',
    DECOMMISSIONED: 'Desativado',
  };

  get isAdmin(): boolean {
    return this.role() === 'ADMIN';
  }

  ngOnInit(): void {
    this.loadList();
  }

  loadList(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    const filters = {
      type: this.filterType() || undefined,
      status: this.filterStatus() || undefined,
    };
    this.maintenanceService.listEquipment(filters).subscribe({
      next: (list) => {
        this.equipment.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar equipamentos.');
        this.loading.set(false);
      },
    });
  }

  applyFilters(): void {
    this.loadList();
  }

  clearFilters(): void {
    this.filterType.set('');
    this.filterStatus.set('');
    this.loadList();
  }

  openDetail(id: string): void {
    this.router.navigate(['/maintenance/equipment', id]);
  }
}
