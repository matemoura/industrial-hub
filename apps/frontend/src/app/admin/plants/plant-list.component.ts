import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { PlantService, PlantResponse, CreatePlantRequest, UpdatePlantRequest } from './plant.service';

type DialogMode = 'create' | 'edit' | null;

const TIMEZONES = [
  'America/Sao_Paulo',
  'America/Manaus',
  'America/Belem',
  'America/Fortaleza',
  'America/Recife',
  'America/Maceio',
  'America/Bahia',
  'America/Cuiaba',
  'America/Porto_Velho',
  'America/Boa_Vista',
  'America/Rio_Branco',
  'America/Noronha',
  'Etc/UTC',
];

@Component({
  selector: 'app-plant-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './plant-list.component.html',
  styleUrl: './plant-list.component.scss',
})
export class PlantListComponent implements OnInit {
  private readonly plantService = inject(PlantService);
  private readonly destroyRef = inject(DestroyRef);

  readonly plants = signal<PlantResponse[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  readonly dialogMode = signal<DialogMode>(null);
  readonly editingPlant = signal<PlantResponse | null>(null);

  // Form state
  readonly formCode = signal('');
  readonly formName = signal('');
  readonly formAddress = signal('');
  readonly formTimezone = signal('America/Sao_Paulo');
  readonly formError = signal<string | null>(null);

  readonly timezones = TIMEZONES;

  readonly isCreateValid = computed(
    () => this.formCode().trim().length > 0 && this.formName().trim().length > 0,
  );

  readonly isEditValid = computed(() => this.formName().trim().length > 0);

  ngOnInit(): void {
    this.loadPlants();
  }

  loadPlants(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.plantService.listPlants()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.plants.set(list);
          this.loading.set(false);
        },
        error: () => {
          this.errorMsg.set('Não foi possível carregar as plantas.');
          this.loading.set(false);
        },
      });
  }

  openCreateDialog(): void {
    this.formCode.set('');
    this.formName.set('');
    this.formAddress.set('');
    this.formTimezone.set('America/Sao_Paulo');
    this.formError.set(null);
    this.editingPlant.set(null);
    this.dialogMode.set('create');
  }

  openEditDialog(plant: PlantResponse): void {
    this.formName.set(plant.name);
    this.formAddress.set(plant.address ?? '');
    this.formTimezone.set(plant.timezone);
    this.formError.set(null);
    this.editingPlant.set(plant);
    this.dialogMode.set('edit');
  }

  closeDialog(): void {
    this.dialogMode.set(null);
    this.editingPlant.set(null);
    this.formError.set(null);
  }

  submitCreate(): void {
    if (!this.isCreateValid() || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);

    const req: CreatePlantRequest = {
      code: this.formCode().trim().toUpperCase(),
      name: this.formName().trim(),
      address: this.formAddress().trim() || null,
      timezone: this.formTimezone(),
    };

    this.plantService.createPlant(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.closeDialog();
          this.showSuccess('Planta criada.');
          this.loadPlants();
        },
        error: (err) => {
          this.formError.set(err?.error?.message ?? 'Erro ao criar planta.');
          this.submitting.set(false);
        },
      });
  }

  submitEdit(): void {
    const plant = this.editingPlant();
    if (!plant || !this.isEditValid() || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);

    const req: UpdatePlantRequest = {
      name: this.formName().trim(),
      address: this.formAddress().trim() || null,
      timezone: this.formTimezone(),
    };

    this.plantService.updatePlant(plant.id, req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.closeDialog();
          this.showSuccess('Planta atualizada.');
          this.loadPlants();
        },
        error: (err) => {
          this.formError.set(err?.error?.message ?? 'Erro ao atualizar planta.');
          this.submitting.set(false);
        },
      });
  }

  deactivate(plant: PlantResponse): void {
    if (plant.isDefault) return;
    if (!confirm(`Desativar a planta "${plant.name}"?`)) return;
    this.plantService.deactivatePlant(plant.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.plants.update((list) => list.filter((p) => p.id !== plant.id));
          this.showSuccess('Planta desativada.');
        },
        error: (err) => {
          this.showError(err?.error?.message ?? 'Erro ao desativar planta.');
        },
      });
  }

  dismissError(): void {
    this.errorMsg.set(null);
  }

  updateFormCode(value: string): void {
    this.formCode.set(value.toUpperCase());
  }

  private showSuccess(message: string): void {
    this.successMsg.set(message);
    this.errorMsg.set(null);
    timer(4000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.successMsg.set(null));
  }

  private showError(message: string): void {
    this.errorMsg.set(message);
    this.successMsg.set(null);
  }
}
