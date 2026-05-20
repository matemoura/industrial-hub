import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import {
  CreateSparePartPayload,
  MaintenanceService,
  SparePartResponse,
  UpdateSparePartPayload,
  UpdateSparePartStockPayload,
} from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';

type DialogMode = 'create' | 'edit' | 'stock' | null;

@Component({
  selector: 'app-spare-part-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ReactiveFormsModule],
  templateUrl: './spare-part-list.component.html',
  styleUrl: './spare-part-list.component.scss',
})
export class SparePartListComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');
  readonly canEdit = computed(() => {
    const r = this.authService.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  readonly parts = signal<SparePartResponse[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly snackbar = signal<{ message: string; type: 'success' | 'error' } | null>(null);

  readonly dialogMode = signal<DialogMode>(null);
  readonly selectedPart = signal<SparePartResponse | null>(null);

  readonly filterCategory = signal('');
  readonly filterBelowMin = signal(false);

  readonly form: FormGroup = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(50)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    category: [''],
    unit: [''],
    stockQty: [0, [Validators.required, Validators.min(0)]],
    minStockQty: [0, [Validators.required, Validators.min(0)]],
  });

  readonly stockForm: FormGroup = this.fb.group({
    quantity: [0, [Validators.required]],
    reason: ['', Validators.required],
  });

  ngOnInit(): void {
    this.loadList();
  }

  loadList(): void {
    this.loading.set(true);
    const filters = {
      category: this.filterCategory().trim() || undefined,
      belowMin: this.filterBelowMin() || undefined,
    };
    this.maintenanceService.listSpareParts(filters).subscribe({
      next: (list) => {
        this.parts.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.showSnackbar('Erro ao carregar peças.', 'error');
      },
    });
  }

  applyFilters(): void {
    this.loadList();
  }

  clearFilters(): void {
    this.filterCategory.set('');
    this.filterBelowMin.set(false);
    this.loadList();
  }

  openCreateDialog(): void {
    this.form.reset({ code: '', name: '', category: '', unit: '', stockQty: 0, minStockQty: 0 });
    this.selectedPart.set(null);
    this.dialogMode.set('create');
  }

  openEditDialog(part: SparePartResponse): void {
    this.form.patchValue({
      code: part.code,
      name: part.name,
      category: part.category ?? '',
      unit: part.unit ?? '',
      stockQty: part.stockQty,
      minStockQty: part.minStockQty,
    });
    this.selectedPart.set(part);
    this.dialogMode.set('edit');
  }

  openStockDialog(part: SparePartResponse): void {
    this.stockForm.reset({ quantity: 0, reason: '' });
    this.selectedPart.set(part);
    this.dialogMode.set('stock');
  }

  closeDialog(): void {
    this.dialogMode.set(null);
    this.selectedPart.set(null);
  }

  submitCreate(): void {
    if (this.form.invalid || this.submitting()) return;
    const v = this.form.value as CreateSparePartPayload;
    this.submitting.set(true);
    this.maintenanceService.createSparePart(v).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.showSnackbar('Peça criada com sucesso.', 'success');
        this.loadList();
      },
      error: (err) => {
        this.submitting.set(false);
        const status = (err as { status?: number })?.status;
        const msg =
          status === 409
            ? 'Código já existe.'
            : ((err as { error?: { message?: string } })?.error?.message ?? 'Erro ao criar peça.');
        this.showSnackbar(msg, 'error');
      },
    });
  }

  submitEdit(): void {
    if (this.form.invalid || this.submitting()) return;
    const part = this.selectedPart();
    if (!part) return;
    const v = this.form.value as UpdateSparePartPayload;
    this.submitting.set(true);
    this.maintenanceService.updateSparePart(part.id, v).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.showSnackbar('Peça atualizada.', 'success');
        this.loadList();
      },
      error: (err) => {
        this.submitting.set(false);
        this.showSnackbar(
          (err as { error?: { message?: string } })?.error?.message ?? 'Erro ao atualizar.',
          'error',
        );
      },
    });
  }

  submitStock(): void {
    if (this.stockForm.invalid || this.submitting()) return;
    const part = this.selectedPart();
    if (!part) return;
    const v = this.stockForm.value as UpdateSparePartStockPayload;
    this.submitting.set(true);
    this.maintenanceService.adjustStock(part.id, v).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.showSnackbar('Estoque ajustado.', 'success');
        this.loadList();
      },
      error: (err) => {
        this.submitting.set(false);
        this.showSnackbar(
          (err as { error?: { message?: string } })?.error?.message ?? 'Erro ao ajustar estoque.',
          'error',
        );
      },
    });
  }

  private showSnackbar(message: string, type: 'success' | 'error'): void {
    this.snackbar.set({ message, type });
    timer(4000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.snackbar.set(null));
  }
}
