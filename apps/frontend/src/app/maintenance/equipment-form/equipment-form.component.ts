import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CreateEquipmentPayload,
  EquipmentType,
  MaintenanceService,
  UpdateEquipmentPayload,
} from '../maintenance.service';

@Component({
  selector: 'app-equipment-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './equipment-form.component.html',
  styleUrl: './equipment-form.component.scss',
})
export class EquipmentFormComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  editId = signal<string | null>(null);
  get isEditMode(): boolean { return this.editId() !== null; }

  code = signal('');
  name = signal('');
  type = signal<EquipmentType | ''>('');
  location = signal('');
  acquiredAt = signal('');
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  readonly equipmentTypes: EquipmentType[] = ['MACHINE', 'TOOL', 'VEHICLE', 'INFRASTRUCTURE'];

  readonly typeLabels: Record<EquipmentType, string> = {
    MACHINE: 'Máquina',
    TOOL: 'Ferramenta',
    VEHICLE: 'Veículo',
    INFRASTRUCTURE: 'Infraestrutura',
  };

  get isValid(): boolean {
    return (this.isEditMode || this.code().trim().length > 0)
      && this.name().trim().length > 0
      && this.type() !== '';
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId.set(id);
      this.loading.set(true);
      this.maintenanceService.getEquipment(id).subscribe({
        next: (eq) => {
          this.name.set(eq.name);
          this.type.set(eq.type);
          this.location.set(eq.location ?? '');
          this.acquiredAt.set(eq.acquiredAt ?? '');
          this.loading.set(false);
        },
        error: () => {
          this.errorMsg.set('Equipamento não encontrado.');
          this.loading.set(false);
        },
      });
    }
  }

  submit(): void {
    if (!this.isValid || this.loading()) return;

    this.loading.set(true);
    this.errorMsg.set(null);

    const id = this.editId();
    if (id) {
      const payload: UpdateEquipmentPayload = {
        name: this.name().trim(),
        type: this.type() as EquipmentType,
        location: this.location().trim() || undefined,
        acquiredAt: this.acquiredAt() || undefined,
      };
      this.maintenanceService.updateEquipment(id, payload).subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/maintenance/equipment', id], {
            state: { toast: 'Equipamento atualizado com sucesso' },
          });
        },
        error: (err) => {
          this.errorMsg.set(err?.error?.message ?? 'Erro ao atualizar equipamento. Tente novamente.');
          this.loading.set(false);
        },
      });
    } else {
      const payload: CreateEquipmentPayload = {
        code: this.code().trim(),
        name: this.name().trim(),
        type: this.type() as EquipmentType,
        location: this.location().trim() || undefined,
        acquiredAt: this.acquiredAt() || undefined,
      };
      this.maintenanceService.createEquipment(payload).subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/maintenance/equipment'], {
            state: { toast: 'Equipamento cadastrado com sucesso' },
          });
        },
        error: (err) => {
          this.errorMsg.set(err?.error?.message ?? 'Erro ao cadastrar equipamento. Tente novamente.');
          this.loading.set(false);
        },
      });
    }
  }
}
