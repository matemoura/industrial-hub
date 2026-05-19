import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminService,
  ALERT_METRIC_LABELS,
  AlertMetric,
  AlertThreshold,
  CreateAlertThresholdPayload,
  UpdateAlertThresholdPayload,
} from '../admin.service';

type DialogMode = 'create' | 'edit' | 'delete' | null;

@Component({
  selector: 'app-alert-thresholds',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './alert-thresholds.component.html',
  styleUrl: './alert-thresholds.component.scss',
})
export class AlertThresholdsComponent implements OnInit {
  private readonly adminService = inject(AdminService);

  readonly thresholds = signal<AlertThreshold[]>([]);
  readonly loading = signal(true);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly dialogMode = signal<DialogMode>(null);
  readonly selectedThreshold = signal<AlertThreshold | null>(null);

  // Form fields
  readonly formMetric = signal<AlertMetric>('OEE_AVG_BELOW');
  readonly formThreshold = signal<number | null>(null);
  readonly formEmailEnabled = signal(false);
  readonly formError = signal<string | null>(null);

  readonly metricLabels = ALERT_METRIC_LABELS;

  readonly allMetrics: AlertMetric[] = [
    'OEE_AVG_BELOW',
    'NC_CRITICAL_ABOVE',
    'WO_URGENT_PENDING_HOURS',
  ];

  /** Metrics not yet configured (no active threshold) — for create dialog */
  readonly availableMetrics = computed<AlertMetric[]>(() => {
    const used = new Set(
      this.thresholds()
        .filter((t) => t.active)
        .map((t) => t.metric),
    );
    return this.allMetrics.filter((m) => !used.has(m));
  });

  ngOnInit(): void {
    this.loadThresholds();
  }

  loadThresholds(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.adminService.getThresholds().subscribe({
      next: (list) => {
        this.thresholds.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar limiares de alerta.');
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    const firstAvailable = this.availableMetrics()[0];
    if (!firstAvailable) return;
    this.formMetric.set(firstAvailable);
    this.formThreshold.set(null);
    this.formEmailEnabled.set(false);
    this.formError.set(null);
    this.dialogMode.set('create');
  }

  openEditDialog(threshold: AlertThreshold): void {
    this.selectedThreshold.set(threshold);
    this.formMetric.set(threshold.metric);
    this.formThreshold.set(threshold.threshold);
    this.formEmailEnabled.set(threshold.emailEnabled);
    this.formError.set(null);
    this.dialogMode.set('edit');
  }

  openDeleteDialog(threshold: AlertThreshold): void {
    this.selectedThreshold.set(threshold);
    this.dialogMode.set('delete');
  }

  closeDialog(): void {
    this.dialogMode.set(null);
    this.selectedThreshold.set(null);
    this.formError.set(null);
  }

  submitCreate(): void {
    const threshold = this.formThreshold();
    if (threshold === null || threshold < 0) {
      this.formError.set('Informe um valor válido para o limiar.');
      return;
    }
    const payload: CreateAlertThresholdPayload = {
      metric: this.formMetric(),
      threshold,
      emailEnabled: this.formEmailEnabled(),
    };
    this.submitting.set(true);
    this.adminService.createThreshold(payload).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.showSuccess('Limiar criado com sucesso.');
        this.loadThresholds();
      },
      error: (err) => {
        this.formError.set(err?.error?.message ?? 'Erro ao criar limiar.');
        this.submitting.set(false);
      },
    });
  }

  submitEdit(): void {
    const t = this.selectedThreshold();
    if (!t) return;
    const threshold = this.formThreshold();
    if (threshold === null || threshold < 0) {
      this.formError.set('Informe um valor válido para o limiar.');
      return;
    }
    const payload: UpdateAlertThresholdPayload = {
      threshold,
      emailEnabled: this.formEmailEnabled(),
    };
    this.submitting.set(true);
    this.adminService.updateThreshold(t.id, payload).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.showSuccess('Limiar atualizado com sucesso.');
        this.loadThresholds();
      },
      error: (err) => {
        this.formError.set(err?.error?.message ?? 'Erro ao atualizar limiar.');
        this.submitting.set(false);
      },
    });
  }

  confirmDelete(): void {
    const t = this.selectedThreshold();
    if (!t) return;
    this.submitting.set(true);
    this.adminService.deleteThreshold(t.id).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.showSuccess('Limiar excluído.');
        this.loadThresholds();
      },
      error: () => {
        this.submitting.set(false);
        this.closeDialog();
        this.errorMsg.set('Erro ao excluir limiar.');
      },
    });
  }

  metricLabel(metric: AlertMetric): string {
    return this.metricLabels[metric];
  }

  dismissError(): void {
    this.errorMsg.set(null);
  }

  private showSuccess(message: string): void {
    this.successMsg.set(message);
    this.errorMsg.set(null);
    setTimeout(() => this.successMsg.set(null), 3000);
  }
}
