import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NcType, NcSeverity, QmsService, SupplierResponse } from '../qms.service';
import { NetworkStatusService } from '../../shared/offline/network-status.service';
import { OfflineQueueService } from '../../shared/offline/offline-queue.service';

@Component({
  selector: 'app-nc-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './nc-form.component.html',
  styleUrl: './nc-form.component.scss',
})
export class NcFormComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly router = inject(Router);
  private readonly networkStatus = inject(NetworkStatusService);
  private readonly offlineQueue = inject(OfflineQueueService);

  title = signal('');
  type = signal<NcType | ''>('');
  severity = signal<NcSeverity | ''>('');
  description = signal('');
  supplierId = signal('');
  loading = signal(false);
  errorMsg = signal<string | null>(null);
  offlineMsg = signal<string | null>(null);

  suppliers = signal<SupplierResponse[]>([]);

  readonly isSupplierType = computed(() => this.type() === 'SUPPLIER');

  readonly ncTypes: NcType[] = ['PROCESS', 'PRODUCT', 'SUPPLIER', 'EQUIPMENT', 'OTHER'];
  readonly ncSeverities: NcSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  readonly typeLabels: Record<NcType, string> = {
    PROCESS: 'Processo',
    PRODUCT: 'Produto',
    SUPPLIER: 'Fornecedor',
    EQUIPMENT: 'Equipamento',
    OTHER: 'Outro',
  };

  readonly severityLabels: Record<NcSeverity, string> = {
    LOW: 'Baixa',
    MEDIUM: 'Média',
    HIGH: 'Alta',
    CRITICAL: 'Crítica',
  };

  get isValid(): boolean {
    const baseValid = this.title().trim().length > 0 && this.type() !== '' && this.severity() !== '';
    if (this.isSupplierType()) {
      return baseValid && this.supplierId() !== '';
    }
    return baseValid;
  }

  ngOnInit(): void {
    this.qmsService.listSuppliers().subscribe({
      next: (list) => this.suppliers.set(list),
    });
  }

  onTypeChange(value: NcType | ''): void {
    this.type.set(value);
    if (value !== 'SUPPLIER') {
      this.supplierId.set('');
    }
  }

  submit(): void {
    if (!this.isValid || this.loading()) return;

    const payload = {
      title: this.title().trim(),
      type: this.type() as NcType,
      severity: this.severity() as NcSeverity,
      description: this.description().trim() || undefined,
      supplierId: this.isSupplierType() ? this.supplierId() : undefined,
    };

    if (!this.networkStatus.isOnline()) {
      void this.offlineQueue.enqueue(payload).then(() => {
        this.offlineMsg.set(
          'Sem conexão. NC salva localmente — será enviada quando a conexão retornar.',
        );
      });
      return;
    }

    this.loading.set(true);
    this.errorMsg.set(null);

    this.qmsService.createNc(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/qms/non-conformances'], {
          state: { toast: 'Não-conformidade registrada com sucesso' },
        });
      },
      error: (err) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao registrar NC. Tente novamente.');
        this.loading.set(false);
      },
    });
  }
}
