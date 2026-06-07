import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  RiskService,
  RiskItemDetail,
  MitigationAction,
  RiskStatus,
  MitigationStatus,
  RiskLevel,
  rpnToRiskLevel,
} from '../../risk.service';
import { AuthService } from '../../../auth/auth.service';

@Component({
  selector: 'app-risk-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './risk-detail.component.html',
  styleUrl: './risk-detail.component.scss',
})
export class RiskDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly riskService = inject(RiskService);
  private readonly authService = inject(AuthService);

  risk = signal<RiskItemDetail | null>(null);
  loading = signal(false);

  readonly isSupervisor = computed(() => {
    const role = this.authService.role();
    return role === 'SUPERVISOR' || role === 'ADMIN';
  });

  // Add mitigation action modal
  showAddActionModal = signal(false);
  addActionLoading = signal(false);
  addActionError = signal<string | null>(null);
  newAction = { description: '', responsible: '', targetDate: '' };

  // Complete action modal
  showCompleteActionModal = signal(false);
  completeActionLoading = signal(false);
  completeActionError = signal<string | null>(null);
  completingAction = signal<MitigationAction | null>(null);

  residualSeverity = signal(5);
  residualOccurrence = signal(5);
  residualDetectability = signal(5);
  readonly residualRpnPreview = computed(
    () => this.residualSeverity() * this.residualOccurrence() * this.residualDetectability()
  );
  readonly residualRiskLevelPreview = computed(() =>
    rpnToRiskLevel(this.residualRpnPreview())
  );

  // Status transition loading
  statusLoading = signal(false);

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

  readonly mitigationStatusLabels: Record<MitigationStatus, string> = {
    PLANNED: 'Planejada',
    IN_PROGRESS: 'Em Andamento',
    COMPLETED: 'Concluída',
  };

  readonly mitigationStatusColors: Record<MitigationStatus, string> = {
    PLANNED: '#818286',
    IN_PROGRESS: '#56A4BB',
    COMPLETED: '#3FA66A',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.riskService.getRisk(id).subscribe({
      next: (r) => {
        this.risk.set(r);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // ── Status transitions ──────────────────────────────────────────────────
  startMitigation(): void {
    this.transitionStatus('BEING_MITIGATED');
  }

  markMitigated(): void {
    this.transitionStatus('MITIGATED');
  }

  acceptRisk(): void {
    this.transitionStatus('ACCEPTED');
  }

  private transitionStatus(status: RiskStatus): void {
    const r = this.risk();
    if (!r) return;
    this.statusLoading.set(true);
    this.riskService.updateRiskStatus(r.id, { status }).subscribe({
      next: (updated) => {
        this.risk.update((cur) => (cur ? { ...cur, ...updated } : cur));
        this.statusLoading.set(false);
      },
      error: () => this.statusLoading.set(false),
    });
  }

  // ── Add mitigation action ───────────────────────────────────────────────
  openAddActionModal(): void {
    this.newAction = { description: '', responsible: '', targetDate: '' };
    this.addActionError.set(null);
    this.showAddActionModal.set(true);
  }

  closeAddActionModal(): void {
    this.showAddActionModal.set(false);
  }

  submitAddAction(): void {
    if (!this.newAction.description || !this.newAction.responsible) {
      this.addActionError.set('Preencha os campos obrigatórios.');
      return;
    }
    const r = this.risk();
    if (!r) return;
    this.addActionLoading.set(true);
    this.addActionError.set(null);
    this.riskService
      .addMitigationAction(r.id, {
        description: this.newAction.description,
        responsible: this.newAction.responsible,
        targetDate: this.newAction.targetDate || undefined,
      })
      .subscribe({
        next: (action) => {
          this.risk.update((cur) =>
            cur ? { ...cur, mitigationActions: [...cur.mitigationActions, action] } : cur
          );
          this.addActionLoading.set(false);
          this.showAddActionModal.set(false);
        },
        error: () => {
          this.addActionLoading.set(false);
          this.addActionError.set('Erro ao adicionar ação. Tente novamente.');
        },
      });
  }

  // ── Complete mitigation action ──────────────────────────────────────────
  openCompleteModal(action: MitigationAction): void {
    this.completingAction.set(action);
    this.residualSeverity.set(5);
    this.residualOccurrence.set(5);
    this.residualDetectability.set(5);
    this.completeActionError.set(null);
    this.showCompleteActionModal.set(true);
  }

  closeCompleteModal(): void {
    this.showCompleteActionModal.set(false);
    this.completingAction.set(null);
  }

  submitCompleteAction(): void {
    const action = this.completingAction();
    const r = this.risk();
    if (!action || !r) return;

    this.completeActionLoading.set(true);
    this.completeActionError.set(null);

    const completedAt = new Date().toISOString().split('T')[0];
    this.riskService
      .updateMitigationAction(r.id, action.id, {
        status: 'COMPLETED',
        completedAt,
        residualSeverity: this.residualSeverity(),
        residualOccurrence: this.residualOccurrence(),
        residualDetectability: this.residualDetectability(),
      })
      .subscribe({
        next: (updated) => {
          this.risk.update((cur) =>
            cur
              ? {
                  ...cur,
                  mitigationActions: cur.mitigationActions.map((a) =>
                    a.id === updated.id ? updated : a
                  ),
                }
              : cur
          );
          this.completeActionLoading.set(false);
          this.showCompleteActionModal.set(false);
          this.completingAction.set(null);
        },
        error: () => {
          this.completeActionLoading.set(false);
          this.completeActionError.set('Erro ao concluir ação. Tente novamente.');
        },
      });
  }

  formatDate(date: string | undefined): string {
    if (!date) return '—';
    return new Date(date + (date.includes('T') ? '' : 'T00:00:00')).toLocaleDateString('pt-BR');
  }
}
