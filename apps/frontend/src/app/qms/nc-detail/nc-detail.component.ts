import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  ActionResponse,
  ActionStatus,
  ActionType,
  NcDocumentLinkResponse,
  NcDocumentLinkType,
  NcResponse,
  NcStatus,
  QmsService,
} from '../qms.service';
import { GedService, DocumentSummary } from '../ged.service';
import { AuthService } from '../../auth/auth.service';
import { NcRcaComponent } from './nc-rca/nc-rca.component';
import { AttachmentListComponent } from '../../shared/attachment/attachment-list.component';
import { SlaBreachedChipComponent } from '../../shared/sla-breached-chip/sla-breached-chip.component';
import { NetworkStatusService } from '../../shared/offline/network-status.service';

@Component({
  selector: 'app-nc-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, NcRcaComponent, AttachmentListComponent, SlaBreachedChipComponent],
  templateUrl: './nc-detail.component.html',
  styleUrl: './nc-detail.component.scss',
})
export class NcDetailComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly gedService = inject(GedService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly networkStatus = inject(NetworkStatusService);

  readonly role = this.authService.role;

  nc = signal<NcResponse | null>(null);
  loading = signal(true);
  errorMsg = signal<string | null>(null);
  transitionLoading = signal(false);

  actions = signal<ActionResponse[]>([]);
  actionsLoading = signal(false);
  actionError = signal<string | null>(null);

  showActionForm = signal(false);
  actionDescription = signal('');
  actionResponsible = signal('');
  actionDueDate = signal('');
  actionSubmitting = signal(false);

  readonly statusLabels: Record<NcStatus, string> = {
    OPEN: 'Aberta',
    IN_ANALYSIS: 'Em análise',
    CLOSED: 'Fechada',
  };

  // ── CAPAS ────────────────────────────────────────────────────────────────────
  editingActionId = signal<string | null>(null);
  capaEditForm = signal<{
    type: ActionType;
    rootCauseConfirmed: string;
    preventiveMeasure: string;
    effectivenessCheckDate: string;
  } | null>(null);

  showVerifyModal = signal(false);
  verifyingActionId = signal<string | null>(null);
  effectivenessResult = signal('');
  effectivenessCheckedBy = signal('');
  capaActionLoading = signal(false);

  readonly typeLabels: Record<ActionType, string> = {
    CORRECTIVE: 'Corretiva',
    PREVENTIVE: 'Preventiva',
  };

  readonly statusLabels2: Record<ActionStatus, string> = {
    PENDING: 'Pendente',
    PENDING_EFFECTIVENESS: 'Aguard. Eficácia',
    DONE: 'Concluída',
  };

  // ── US-115: NC↔GED Link ───────────────────────────────────────────────────

  ncDocLinks = signal<NcDocumentLinkResponse[]>([]);
  ncDocLinksLoading = signal(false);
  ncDocLinksError = signal<string | null>(null);

  showLinkDocModal = signal(false);
  linkDocSearch = signal('');
  linkDocSearchResults = signal<DocumentSummary[]>([]);
  linkDocSearchLoading = signal(false);
  linkDocSelectedId = signal<string | null>(null);
  linkDocLinkType = signal<NcDocumentLinkType>('PROCEDURE_AT_OCCURRENCE');
  linkDocSubmitting = signal(false);
  linkDocError = signal<string | null>(null);

  readonly linkTypeLabels: Record<NcDocumentLinkType, string> = {
    PROCEDURE_AT_OCCURRENCE: 'Procedimento na Ocorrência',
    CORRECTIVE_REFERENCE: 'Referência Corretiva',
    OTHER: 'Outro',
  };

  readonly docStatusLabels: Record<string, string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicado',
    OBSOLETE: 'Obsoleto',
  };

  readonly docStatusColors: Record<string, string> = {
    DRAFT: '#E8A93C',
    PUBLISHED: '#3FA66A',
    OBSOLETE: '#818286',
  };

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  get allowedTransitions(): { label: string; target: NcStatus }[] {
    const status = this.nc()?.status;
    if (!status || !this.isSupervisor) return [];
    if (status === 'OPEN') return [{ label: 'Iniciar Análise', target: 'IN_ANALYSIS' }];
    if (status === 'IN_ANALYSIS') return [
      { label: 'Fechar NC', target: 'CLOSED' },
      { label: 'Re-abrir', target: 'OPEN' },
    ];
    return [];
  }

  get isActionFormValid(): boolean {
    return this.actionDescription().trim().length > 0
      && this.actionResponsible().trim().length > 0
      && this.actionDueDate().length > 0;
  }

  ngOnInit(): void {
    if (!this.networkStatus.isOnline()) {
      this.errorMsg.set('Sem conexão. Não é possível carregar os dados offline.');
      this.loading.set(false);
      return;
    }
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadNc(id);
    this.loadActions(id);
    this.loadNcDocLinks(id);
  }

  loadNc(id: string): void {
    this.loading.set(true);
    this.qmsService.getNc(id).subscribe({
      next: (nc) => {
        this.nc.set(nc);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('NC não encontrada.');
        this.loading.set(false);
      },
    });
  }

  loadActions(id: string): void {
    this.actionsLoading.set(true);
    this.qmsService.listActions(id).subscribe({
      next: (list) => {
        this.actions.set(list);
        this.actionsLoading.set(false);
      },
      error: () => {
        this.actionsLoading.set(false);
      },
    });
  }

  // ── NC↔GED Link methods ──────────────────────────────────────────────────

  loadNcDocLinks(ncId: string): void {
    this.ncDocLinksLoading.set(true);
    this.qmsService.listNcDocuments(ncId).subscribe({
      next: (links) => {
        this.ncDocLinks.set(links);
        this.ncDocLinksLoading.set(false);
      },
      error: () => {
        this.ncDocLinksLoading.set(false);
      },
    });
  }

  openLinkDocModal(): void {
    this.showLinkDocModal.set(true);
    this.linkDocSearch.set('');
    this.linkDocSearchResults.set([]);
    this.linkDocSelectedId.set(null);
    this.linkDocLinkType.set('PROCEDURE_AT_OCCURRENCE');
    this.linkDocError.set(null);
  }

  closeLinkDocModal(): void {
    this.showLinkDocModal.set(false);
  }

  searchDocuments(): void {
    const q = this.linkDocSearch().trim();
    if (q.length < 2) return;
    this.linkDocSearchLoading.set(true);
    this.gedService.listDocuments({ status: 'PUBLISHED' }).subscribe({
      next: (page) => {
        const lower = q.toLowerCase();
        this.linkDocSearchResults.set(
          page.content.filter(d =>
            d.code.toLowerCase().includes(lower) ||
            d.title.toLowerCase().includes(lower)
          ).slice(0, 10)
        );
        this.linkDocSearchLoading.set(false);
      },
      error: () => {
        this.linkDocSearchLoading.set(false);
      },
    });
  }

  selectLinkDoc(docId: string): void {
    this.linkDocSelectedId.set(docId);
  }

  submitLinkDoc(): void {
    const nc = this.nc();
    const docId = this.linkDocSelectedId();
    if (!nc || !docId || this.linkDocSubmitting()) return;

    this.linkDocSubmitting.set(true);
    this.linkDocError.set(null);
    this.qmsService.linkNcToDocument(nc.id, {
      documentId: docId,
      linkType: this.linkDocLinkType(),
    }).subscribe({
      next: (link) => {
        this.ncDocLinks.update(list => [link, ...list]);
        this.linkDocSubmitting.set(false);
        this.closeLinkDocModal();
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Erro ao vincular documento.';
        this.linkDocError.set(msg);
        this.linkDocSubmitting.set(false);
      },
    });
  }

  unlinkDocument(documentId: string): void {
    const nc = this.nc();
    if (!nc || !confirm('Desvincular este documento da NC?')) return;

    this.qmsService.unlinkNcDocument(nc.id, documentId).subscribe({
      next: () => {
        this.ncDocLinks.update(list => list.filter(l => l.documentId !== documentId));
      },
      error: (err) => {
        this.ncDocLinksError.set(err?.error?.message ?? 'Erro ao desvincular documento.');
      },
    });
  }

  // ── Shared helpers ────────────────────────────────────────────────────────

  transition(target: NcStatus): void {
    const nc = this.nc();
    if (!nc || this.transitionLoading()) return;

    const labelMap: Record<NcStatus, string> = {
      IN_ANALYSIS: 'iniciar análise',
      CLOSED: 'fechar',
      OPEN: 're-abrir',
    };
    if (!confirm(`Confirma ${labelMap[target]} esta NC?`)) return;

    this.transitionLoading.set(true);
    this.errorMsg.set(null);
    this.qmsService.transitionStatus(nc.id, target).subscribe({
      next: (updated) => {
        this.nc.set(updated);
        this.transitionLoading.set(false);
      },
      error: (err) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao atualizar status. Tente novamente.');
        this.transitionLoading.set(false);
      },
    });
  }

  submitAction(): void {
    const nc = this.nc();
    if (!nc || !this.isActionFormValid || this.actionSubmitting()) return;

    this.actionSubmitting.set(true);
    this.actionError.set(null);

    this.qmsService.createAction(nc.id, {
      description: this.actionDescription().trim(),
      responsible: this.actionResponsible().trim(),
      dueDate: this.actionDueDate(),
    }).subscribe({
      next: (action) => {
        this.actions.update(list => [...list, action]);
        this.actionDescription.set('');
        this.actionResponsible.set('');
        this.actionDueDate.set('');
        this.showActionForm.set(false);
        this.actionSubmitting.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao criar ação. Tente novamente.');
        this.actionSubmitting.set(false);
      },
    });
  }

  completeAction(actionId: string): void {
    const nc = this.nc();
    if (!nc || !confirm('Confirma conclusão desta ação?')) return;

    this.qmsService.completeAction(nc.id, actionId).subscribe({
      next: (updated) => {
        this.actions.update(list => list.map(a => a.id === actionId ? updated : a));
        this.loadNc(nc.id);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao concluir ação.');
      },
    });
  }

  deleteAction(actionId: string): void {
    const nc = this.nc();
    if (!nc || !confirm('Excluir esta ação corretiva?')) return;

    this.qmsService.deleteAction(nc.id, actionId).subscribe({
      next: () => {
        this.actions.update(list => list.filter(a => a.id !== actionId));
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao excluir ação.');
      },
    });
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return iso.replace('T', ' ').slice(0, 16);
  }

  formatLocalDate(date: string | null): string {
    if (!date) return '—';
    return date;
  }

  startEditCapa(action: ActionResponse): void {
    this.editingActionId.set(action.id);
    this.capaEditForm.set({
      type: action.type ?? 'CORRECTIVE',
      rootCauseConfirmed: action.rootCauseConfirmed ?? '',
      preventiveMeasure: action.preventiveMeasure ?? '',
      effectivenessCheckDate: action.effectivenessCheckDate ?? '',
    });
  }

  cancelEditCapa(): void {
    this.editingActionId.set(null);
    this.capaEditForm.set(null);
  }

  saveCapaEdit(action: ActionResponse): void {
    const nc = this.nc();
    const form = this.capaEditForm();
    if (!nc || !form || this.capaActionLoading()) return;

    this.capaActionLoading.set(true);
    this.qmsService.updateCapa(nc.id, action.id, {
      type: form.type,
      rootCauseConfirmed: form.rootCauseConfirmed || undefined,
      preventiveMeasure: form.preventiveMeasure || undefined,
      effectivenessCheckDate: form.effectivenessCheckDate || undefined,
    }).subscribe({
      next: (updated) => {
        this.actions.update(list => list.map(a => a.id === action.id ? updated : a));
        this.editingActionId.set(null);
        this.capaEditForm.set(null);
        this.capaActionLoading.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao salvar CAPA.');
        this.capaActionLoading.set(false);
      },
    });
  }

  submitForEffectiveness(action: ActionResponse): void {
    const nc = this.nc();
    if (!nc || this.capaActionLoading()) return;

    this.capaActionLoading.set(true);
    this.qmsService.submitForEffectiveness(nc.id, action.id).subscribe({
      next: (updated) => {
        this.actions.update(list => list.map(a => a.id === action.id ? updated : a));
        this.capaActionLoading.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao enviar para verificação.');
        this.capaActionLoading.set(false);
      },
    });
  }

  openVerifyModal(action: ActionResponse): void {
    this.verifyingActionId.set(action.id);
    this.effectivenessResult.set('');
    this.effectivenessCheckedBy.set('');
    this.showVerifyModal.set(true);
  }

  confirmVerify(ncId: string): void {
    const actionId = this.verifyingActionId();
    if (!actionId || this.capaActionLoading()) return;

    this.capaActionLoading.set(true);
    this.qmsService.verifyEffectiveness(ncId, actionId, {
      effectivenessResult: this.effectivenessResult(),
      effectivenessCheckedBy: this.effectivenessCheckedBy(),
    }).subscribe({
      next: (updated) => {
        this.actions.update(list => list.map(a => a.id === actionId ? updated : a));
        this.showVerifyModal.set(false);
        this.verifyingActionId.set(null);
        this.capaActionLoading.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao confirmar eficácia.');
        this.capaActionLoading.set(false);
      },
    });
  }
}
