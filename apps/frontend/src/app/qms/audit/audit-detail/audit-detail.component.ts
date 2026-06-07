import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import {
  AuditService,
  InternalAuditDetail,
  AuditChecklistItem,
  AuditFinding,
  AuditStatus,
  ChecklistResponse,
  FindingType,
  NcSeverity,
  CreateFindingRequest,
  CreateChecklistItemRequest,
} from '../../audit.service';
import { AuthService } from '../../../auth/auth.service';

@Component({
  selector: 'app-audit-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './audit-detail.component.html',
  styleUrl: './audit-detail.component.scss',
})
export class AuditDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly auditService = inject(AuditService);
  private readonly authService = inject(AuthService);
  private readonly destroy$ = new Subject<void>();

  // debounce subjects per checklist item id
  private readonly checklistSubjects = new Map<string, Subject<{ response: ChecklistResponse; evidence: string }>>();

  audit = signal<InternalAuditDetail | null>(null);
  loading = signal(false);
  isSavingChecklist = signal(false);
  toast = signal<string | null>(null);

  readonly isSupervisor = computed(() => {
    const role = this.authService.role();
    return role === 'SUPERVISOR' || role === 'ADMIN';
  });

  // local checklist edits (indexed by item id)
  checklistResponses: Record<string, ChecklistResponse | ''> = {};
  checklistEvidences: Record<string, string> = {};

  // Add finding modal
  showAddFindingModal = signal(false);
  newFinding = {
    type: 'NON_CONFORMANCE' as FindingType,
    severity: 'MEDIUM' as NcSeverity,
    isoClause: '',
    description: '',
    linkedNcId: '',
  };
  findingError = signal<string | null>(null);

  // Add checklist item modal
  showAddChecklistModal = signal(false);
  newChecklistItem = { process: '', isoClause: '', question: '' };
  checklistItemError = signal<string | null>(null);

  readonly statusColors: Record<AuditStatus, string> = {
    PLANNED: '#818286',
    IN_PROGRESS: '#56A4BB',
    COMPLETED: '#3FA66A',
    CANCELLED: '#1F3A4A',
  };

  readonly statusLabels: Record<AuditStatus, string> = {
    PLANNED: 'Planejada',
    IN_PROGRESS: 'Em Andamento',
    COMPLETED: 'Concluída',
    CANCELLED: 'Cancelada',
  };

  readonly findingTypeColors: Record<FindingType, string> = {
    NON_CONFORMANCE: '#D24A4A',
    OBSERVATION: '#E8A93C',
    OPPORTUNITY_FOR_IMPROVEMENT: '#56A4BB',
  };

  readonly findingTypeLabels: Record<FindingType, string> = {
    NON_CONFORMANCE: 'Não-Conformidade',
    OBSERVATION: 'Observação',
    OPPORTUNITY_FOR_IMPROVEMENT: 'Oportunidade de Melhoria',
  };

  readonly severityLabels: Record<NcSeverity, string> = {
    LOW: 'Baixa',
    MEDIUM: 'Média',
    HIGH: 'Alta',
    CRITICAL: 'Crítica',
  };

  readonly responseLabels: Record<ChecklistResponse, string> = {
    CONFORMING: 'Conforme',
    NON_CONFORMING: 'Não-Conforme',
    OBSERVATION: 'Observação',
    NOT_APPLICABLE: 'N/A',
  };

  readonly responseColors: Record<ChecklistResponse, string> = {
    CONFORMING: '#3FA66A',
    NON_CONFORMING: '#D24A4A',
    OBSERVATION: '#E8A93C',
    NOT_APPLICABLE: '#818286',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.load(id);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.checklistSubjects.forEach((s) => s.complete());
  }

  private load(id: string): void {
    this.loading.set(true);
    this.auditService.getAudit(id).subscribe({
      next: (a) => {
        this.audit.set(a);
        a.checklistItems.forEach((item) => {
          this.checklistResponses[item.id] = item.response ?? '';
          this.checklistEvidences[item.id] = item.evidence ?? '';
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onResponseChange(item: AuditChecklistItem, response: ChecklistResponse): void {
    this.checklistResponses[item.id] = response;
    this.scheduleChecklistSave(item.id, response, this.checklistEvidences[item.id] ?? '');
  }

  onEvidenceChange(item: AuditChecklistItem, evidence: string): void {
    this.checklistEvidences[item.id] = evidence;
    const resp = this.checklistResponses[item.id];
    if (resp) {
      this.scheduleChecklistSave(item.id, resp as ChecklistResponse, evidence);
    }
  }

  private scheduleChecklistSave(itemId: string, response: ChecklistResponse, evidence: string): void {
    if (!this.checklistSubjects.has(itemId)) {
      const subject = new Subject<{ response: ChecklistResponse; evidence: string }>();
      this.checklistSubjects.set(itemId, subject);
      subject
        .pipe(debounceTime(800), distinctUntilChanged(), takeUntil(this.destroy$))
        .subscribe((payload) => {
          const auditId = this.audit()?.id;
          if (!auditId) return;
          this.isSavingChecklist.set(true);
          this.auditService
            .updateChecklistItem(auditId, itemId, payload)
            .subscribe({ complete: () => this.isSavingChecklist.set(false) });
        });
    }
    this.checklistSubjects.get(itemId)!.next({ response, evidence });
  }

  // ── Status transitions ───────────────────────────────────────────────
  startAudit(): void {
    const a = this.audit();
    if (!a) return;
    this.auditService.updateAuditStatus(a.id, { status: 'IN_PROGRESS' }).subscribe((updated) => {
      this.audit.update((cur) => (cur ? { ...cur, ...updated } : cur));
    });
  }

  cancelAudit(): void {
    const a = this.audit();
    if (!a) return;
    this.auditService.updateAuditStatus(a.id, { status: 'CANCELLED' }).subscribe((updated) => {
      this.audit.update((cur) => (cur ? { ...cur, ...updated } : cur));
    });
  }

  completeAudit(): void {
    const a = this.audit();
    if (!a) return;
    const dateStr = new Date().toISOString().split('T')[0];
    this.auditService
      .updateAuditStatus(a.id, { status: 'COMPLETED', completedDate: dateStr })
      .subscribe((updated) => {
        this.audit.update((cur) => (cur ? { ...cur, ...updated } : cur));
      });
  }

  // ── PDF download ─────────────────────────────────────────────────────
  downloadReport(): void {
    const a = this.audit();
    if (!a) return;
    this.auditService.generateReport(a.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `audit-${a.code}.pdf`;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }

  // ── Findings ─────────────────────────────────────────────────────────
  openAddFindingModal(): void {
    this.newFinding = { type: 'NON_CONFORMANCE', severity: 'MEDIUM', isoClause: '', description: '', linkedNcId: '' };
    this.findingError.set(null);
    this.showAddFindingModal.set(true);
  }

  closeAddFindingModal(): void {
    this.showAddFindingModal.set(false);
  }

  submitFinding(): void {
    if (!this.newFinding.description || !this.newFinding.isoClause) {
      this.findingError.set('Preencha todos os campos obrigatórios.');
      return;
    }
    const a = this.audit();
    if (!a) return;
    const req: CreateFindingRequest = {
      type: this.newFinding.type,
      severity: this.newFinding.severity,
      isoClause: this.newFinding.isoClause,
      description: this.newFinding.description,
      linkedNcId: this.newFinding.linkedNcId || undefined,
    };
    this.auditService.addFinding(a.id, req).subscribe({
      next: (finding) => {
        this.audit.update((cur) => (cur ? { ...cur, findings: [...cur.findings, finding] } : cur));
        this.showAddFindingModal.set(false);
      },
      error: () => this.findingError.set('Erro ao salvar achado.'),
    });
  }

  deleteFinding(finding: AuditFinding): void {
    const a = this.audit();
    if (!a) return;
    this.auditService.deleteFinding(a.id, finding.id).subscribe(() => {
      this.audit.update((cur) =>
        cur ? { ...cur, findings: cur.findings.filter((f) => f.id !== finding.id) } : cur
      );
    });
  }

  // ── Checklist items ──────────────────────────────────────────────────
  openAddChecklistModal(): void {
    this.newChecklistItem = { process: '', isoClause: '', question: '' };
    this.checklistItemError.set(null);
    this.showAddChecklistModal.set(true);
  }

  closeAddChecklistModal(): void {
    this.showAddChecklistModal.set(false);
  }

  submitChecklistItem(): void {
    if (!this.newChecklistItem.process || !this.newChecklistItem.isoClause || !this.newChecklistItem.question) {
      this.checklistItemError.set('Preencha todos os campos.');
      return;
    }
    const a = this.audit();
    if (!a) return;
    const items: CreateChecklistItemRequest[] = [{ ...this.newChecklistItem }];
    this.auditService.addChecklistItems(a.id, items).subscribe({
      next: (newItems) => {
        this.audit.update((cur) =>
          cur ? { ...cur, checklistItems: [...cur.checklistItems, ...newItems] } : cur
        );
        newItems.forEach((item) => {
          this.checklistResponses[item.id] = '';
          this.checklistEvidences[item.id] = '';
        });
        this.showAddChecklistModal.set(false);
      },
      error: () => this.checklistItemError.set('Erro ao adicionar item.'),
    });
  }

  formatDate(date: string | undefined): string {
    if (!date) return '—';
    return new Date(date + 'T00:00:00').toLocaleDateString('pt-BR');
  }
}
