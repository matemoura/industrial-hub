import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  RiskService,
  RiskItem,
  RiskSummary,
  RiskLevel,
  RiskStatus,
  PageResponse,
  rpnToRiskLevel,
} from '../../risk.service';
import { AuthService } from '../../../auth/auth.service';

@Component({
  selector: 'app-risk-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './risk-list.component.html',
  styleUrl: './risk-list.component.scss',
})
export class RiskListComponent implements OnInit {
  private readonly riskService = inject(RiskService);
  private readonly authService = inject(AuthService);

  readonly isSupervisor = computed(() => {
    const role = this.authService.role();
    return role === 'SUPERVISOR' || role === 'ADMIN';
  });

  risks = signal<RiskItem[]>([]);
  summary = signal<RiskSummary | null>(null);
  loading = signal(false);
  totalElements = signal(0);
  page = signal(0);
  readonly pageSize = 20;

  riskLevelFilter = signal<RiskLevel | ''>('');
  statusFilter = signal<RiskStatus | ''>('');

  showNewModal = signal(false);
  modalLoading = signal(false);
  modalError = signal<string | null>(null);

  // Separate signals so computed() tracks them reactively
  newSeverity = signal(5);
  newOccurrence = signal(5);
  newDetectability = signal(5);

  newRisk = {
    process: '',
    failureMode: '',
    failureEffect: '',
    failureCause: '',
    owner: '',
    linkedNcId: '',
  };

  readonly previewRpn = computed(
    () => this.newSeverity() * this.newOccurrence() * this.newDetectability()
  );

  readonly previewRiskLevel = computed(() => rpnToRiskLevel(this.previewRpn()));

  readonly riskLevelColors: Record<RiskLevel, string> = {
    CRITICAL: '#D24A4A',
    HIGH: '#F97316',
    MEDIUM: '#E8A93C',
    LOW: '#3FA66A',
  };

  readonly riskLevelLabels: Record<RiskLevel, string> = {
    CRITICAL: 'Crítico',
    HIGH: 'Alto',
    MEDIUM: 'Médio',
    LOW: 'Baixo',
  };

  readonly statusLabels: Record<RiskStatus, string> = {
    IDENTIFIED: 'Identificado',
    BEING_MITIGATED: 'Em Mitigação',
    MITIGATED: 'Mitigado',
    ACCEPTED: 'Aceito',
  };

  readonly statusColors: Record<RiskStatus, string> = {
    IDENTIFIED: '#818286',
    BEING_MITIGATED: '#56A4BB',
    MITIGATED: '#3FA66A',
    ACCEPTED: '#1F3A4A',
  };

  ngOnInit(): void {
    this.loadSummary();
    this.loadRisks();
  }

  private loadSummary(): void {
    this.riskService.getRiskSummary().subscribe({
      next: (s) => this.summary.set(s),
    });
  }

  loadRisks(): void {
    this.loading.set(true);
    this.riskService
      .listRisks({
        riskLevel: this.riskLevelFilter() || undefined,
        status: this.statusFilter() || undefined,
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (p: PageResponse<RiskItem>) => {
          this.risks.set(p.content);
          this.totalElements.set(p.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onFilterChange(): void {
    this.page.set(0);
    this.loadRisks();
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.loadRisks();
    }
  }

  nextPage(): void {
    const maxPage = Math.ceil(this.totalElements() / this.pageSize) - 1;
    if (this.page() < maxPage) {
      this.page.update((p) => p + 1);
      this.loadRisks();
    }
  }

  openNewModal(): void {
    this.newRisk = {
      process: '',
      failureMode: '',
      failureEffect: '',
      failureCause: '',
      owner: '',
      linkedNcId: '',
    };
    this.newSeverity.set(5);
    this.newOccurrence.set(5);
    this.newDetectability.set(5);
    this.modalError.set(null);
    this.showNewModal.set(true);
  }

  closeNewModal(): void {
    this.showNewModal.set(false);
  }

  submitNewRisk(): void {
    if (
      !this.newRisk.process ||
      !this.newRisk.failureMode ||
      !this.newRisk.failureEffect ||
      !this.newRisk.failureCause ||
      !this.newRisk.owner
    ) {
      this.modalError.set('Preencha todos os campos obrigatórios.');
      return;
    }
    this.modalLoading.set(true);
    this.modalError.set(null);
    this.riskService
      .createRisk({
        process: this.newRisk.process,
        failureMode: this.newRisk.failureMode,
        failureEffect: this.newRisk.failureEffect,
        failureCause: this.newRisk.failureCause,
        severity: this.newSeverity(),
        occurrence: this.newOccurrence(),
        detectability: this.newDetectability(),
        owner: this.newRisk.owner,
        linkedNcId: this.newRisk.linkedNcId || undefined,
      })
      .subscribe({
        next: () => {
          this.modalLoading.set(false);
          this.showNewModal.set(false);
          this.loadSummary();
          this.loadRisks();
        },
        error: () => {
          this.modalLoading.set(false);
          this.modalError.set('Erro ao criar risco. Tente novamente.');
        },
      });
  }

  truncate(text: string, max: number): string {
    return text.length > max ? text.slice(0, max) + '…' : text;
  }

  rpnColor(rpn: number): string {
    return rpn > 200 ? '#D24A4A' : 'inherit';
  }
}
