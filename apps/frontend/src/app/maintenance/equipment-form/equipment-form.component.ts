import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CreateEquipmentPayload, EquipmentType, MaintenanceService } from '../maintenance.service';

@Component({
  selector: 'app-equipment-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './equipment-form.component.html',
  styleUrl: './equipment-form.component.scss',
})
export class EquipmentFormComponent {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly router = inject(Router);

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
    return this.code().trim().length > 0
      && this.name().trim().length > 0
      && this.type() !== '';
  }

  submit(): void {
    if (!this.isValid || this.loading()) return;

    this.loading.set(true);
    this.errorMsg.set(null);

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
