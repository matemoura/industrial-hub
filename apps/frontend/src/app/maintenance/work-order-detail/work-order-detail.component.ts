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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AddWorkOrderPartPayload,
  MaintenanceService,
  SparePartResponse,
  WorkOrderPartResponse,
  WorkOrderResponse,
  WorkOrderStatus,
} from '../maintenance.service';
import { AuthService } from '../../auth/auth.service';
import { AttachmentListComponent } from '../../shared/attachment/attachment-list.component';
import { SlaBreachedChipComponent } from '../../shared/sla-breached-chip/sla-breached-chip.component';

@Component({
  selector: 'app-work-order-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AttachmentListComponent, SlaBreachedChipComponent],
  templateUrl: './work-order-detail.component.html',
  styleUrl: './work-order-detail.component.scss',
})
export class WorkOrderDetailComponent implements OnInit {
  private readonly maintenanceService = inject(MaintenanceService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly canEdit = computed(() => {
    const r = this.authService.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  readonly workOrderId = signal('');
  readonly workOrder = signal<WorkOrderResponse | null>(null);
  readonly parts = signal<WorkOrderPartResponse[]>([]);
  readonly spareParts = signal<SparePartResponse[]>([]);

  readonly loading = signal(true);
  readonly partsLoading = signal(false);
  readonly submitting = signal(false);
  readonly snackbar = signal<{ message: string; type: 'success' | 'error' } | null>(null);

  readonly showAddForm = signal(false);
  readonly confirmRemoveId = signal<string | null>(null);

  readonly addForm: FormGroup = this.fb.group({
    sparePartId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(1)]],
  });

  readonly woStatusLabels: Record<WorkOrderStatus, string> = {
    OPEN: 'Aberta',
    IN_PROGRESS: 'Em andamento',
    DONE: 'Concluída',
    CANCELLED: 'Cancelada',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.workOrderId.set(id);
    this.loadWorkOrder(id);
    this.loadParts(id);
    this.loadSpareParts();
  }

  loadWorkOrder(id: string): void {
    this.loading.set(true);
    this.maintenanceService.getWorkOrder(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (wo) => {
          this.workOrder.set(wo);
          this.loading.set(false);
        },
        error: () => {
          this.workOrder.set(null);
          this.loading.set(false);
        },
      });
  }

  loadParts(workOrderId: string): void {
    this.partsLoading.set(true);
    this.maintenanceService.listWorkOrderParts(workOrderId).subscribe({
      next: (list) => {
        this.parts.set(list);
        this.partsLoading.set(false);
      },
      error: () => {
        this.partsLoading.set(false);
        this.showSnackbar('Erro ao carregar peças da OS.', 'error');
      },
    });
  }

  loadSpareParts(): void {
    this.maintenanceService.listSpareParts().subscribe({
      next: (list) => this.spareParts.set(list),
      error: () => { /* non-blocking */ },
    });
  }

  openAddForm(): void {
    this.addForm.reset({ sparePartId: '', quantity: 1 });
    this.showAddForm.set(true);
  }

  cancelAddForm(): void {
    this.showAddForm.set(false);
  }

  submitAddPart(): void {
    if (this.addForm.invalid || this.submitting()) return;
    const payload = this.addForm.value as AddWorkOrderPartPayload;
    const woId = this.workOrderId();
    this.submitting.set(true);
    this.maintenanceService.addWorkOrderPart(woId, payload).subscribe({
      next: (part) => {
        this.parts.update((list) => [...list, part]);
        this.showAddForm.set(false);
        this.submitting.set(false);
        this.showSnackbar('Peça adicionada.', 'success');
      },
      error: (err) => {
        this.submitting.set(false);
        const status = (err as { status?: number })?.status;
        const msg =
          status === 422
            ? ((err as { error?: { message?: string } })?.error?.message ?? 'Estoque insuficiente.')
            : ((err as { error?: { message?: string } })?.error?.message ?? 'Erro ao adicionar peça.');
        this.showSnackbar(msg, 'error');
      },
    });
  }

  openConfirmRemove(partId: string): void {
    this.confirmRemoveId.set(partId);
  }

  cancelRemove(): void {
    this.confirmRemoveId.set(null);
  }

  confirmRemove(): void {
    const partId = this.confirmRemoveId();
    if (!partId) return;
    const woId = this.workOrderId();
    this.submitting.set(true);
    this.maintenanceService.removeWorkOrderPart(woId, partId).subscribe({
      next: () => {
        this.parts.update((list) => list.filter((p) => p.id !== partId));
        this.confirmRemoveId.set(null);
        this.submitting.set(false);
        this.showSnackbar('Peça removida.', 'success');
      },
      error: () => {
        this.submitting.set(false);
        this.confirmRemoveId.set(null);
        this.showSnackbar('Erro ao remover peça.', 'error');
      },
    });
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return iso.replace('T', ' ').slice(0, 16);
  }

  private showSnackbar(message: string, type: 'success' | 'error'): void {
    this.snackbar.set({ message, type });
    timer(4000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.snackbar.set(null));
  }
}
