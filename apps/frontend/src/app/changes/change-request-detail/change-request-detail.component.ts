import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import {
  ChangeRequestService,
  ChangeRequestDetail,
  ChangeEntityType,
  ChangeRequestLink,
} from '../change-request.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-change-request-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './change-request-detail.component.html',
  styleUrl: './change-request-detail.component.scss',
})
export class ChangeRequestDetailComponent implements OnInit {
  private readonly changeRequestService = inject(ChangeRequestService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly role = computed(() => this.authService.role());
  readonly isSupervisor = computed(() => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN');
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly currentUsername = computed(() => this.authService.username() ?? '');

  change = signal<ChangeRequestDetail | null>(null);
  loading = signal(false);
  actionLoading = signal(false);
  actionError = signal<string | null>(null);

  showEditModal = signal(false);
  showReviewModal = signal(false);
  showRejectModal = signal(false);
  showAddLinkModal = signal(false);

  rejectionReason = signal('');
  reviewImpact = signal('');
  reviewRecommend = signal(true);

  newLink: { entityType: ChangeEntityType; entityId: string; linkNote: string } = {
    entityType: 'NON_CONFORMANCE',
    entityId: '',
    linkNote: '',
  };

  editForm = {
    title: '',
    description: '',
    changeType: 'PROCESS' as import('../change-request.service').ChangeType,
    justification: '',
  };

  readonly statusLabels: Record<string, string> = {
    DRAFT: 'Rascunho',
    SUBMITTED: 'Submetida',
    UNDER_REVIEW: 'Em Revisão',
    APPROVED: 'Aprovada',
    REJECTED: 'Rejeitada',
    IMPLEMENTED: 'Implementada',
  };

  readonly statusColors: Record<string, string> = {
    DRAFT: '#818286',
    SUBMITTED: '#56A4BB',
    UNDER_REVIEW: '#E8A93C',
    APPROVED: '#3FA66A',
    REJECTED: '#D24A4A',
    IMPLEMENTED: '#1F3A4A',
  };

  readonly typeLabels: Record<string, string> = {
    PROCESS: 'Processo',
    DOCUMENT: 'Documento',
    EQUIPMENT: 'Equipamento',
    SOFTWARE: 'Software',
    REGULATORY: 'Regulatório',
    OTHER: 'Outro',
  };

  readonly entityTypeLabels: Record<ChangeEntityType, string> = {
    NON_CONFORMANCE: 'Não-Conformidade',
    DOCUMENT: 'Documento',
    EQUIPMENT: 'Equipamento',
    RISK_ITEM: 'Item de Risco',
  };

  readonly timelineSteps = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'IMPLEMENTED'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadChange(id);
    }
  }

  private loadChange(id: string): void {
    this.loading.set(true);
    this.changeRequestService.getChange(id).subscribe({
      next: (cr) => {
        this.change.set(cr);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  getEntityLink(entityType: ChangeEntityType, entityId: string): string {
    switch (entityType) {
      case 'NON_CONFORMANCE': return `/qms/non-conformances/${entityId}`;
      case 'DOCUMENT': return `/qms/documents/${entityId}`;
      case 'EQUIPMENT': return `/maintenance/equipment/${entityId}`;
      case 'RISK_ITEM': return `/qms/risks/${entityId}`;
    }
  }

  getLinksByType(entityType: ChangeEntityType): ChangeRequestLink[] {
    return (this.change()?.links ?? []).filter((l) => l.entityType === entityType);
  }

  getEntityTypes(): ChangeEntityType[] {
    return ['NON_CONFORMANCE', 'DOCUMENT', 'EQUIPMENT', 'RISK_ITEM'];
  }

  getStepState(step: string): 'done' | 'current' | 'future' | 'rejected' {
    const cr = this.change();
    if (!cr) return 'future';
    const status = cr.status;
    if (status === 'REJECTED') {
      const beforeRejected = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW'];
      if (step === 'REJECTED') return 'current';
      if (beforeRejected.indexOf(step) < beforeRejected.indexOf('UNDER_REVIEW') || step === 'UNDER_REVIEW') return 'done';
      return 'future';
    }
    const order = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'IMPLEMENTED'];
    const currentIdx = order.indexOf(status);
    const stepIdx = order.indexOf(step);
    if (stepIdx < currentIdx) return 'done';
    if (stepIdx === currentIdx) return 'current';
    return 'future';
  }

  // Actions
  submitForReview(): void {
    const cr = this.change();
    if (!cr) return;
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.changeRequestService.submitChange(cr.id).subscribe({
      next: (updated) => {
        this.change.update((c) => (c ? { ...c, ...updated } : c));
        this.actionLoading.set(false);
      },
      error: () => {
        this.actionLoading.set(false);
        this.actionError.set('Erro ao submeter solicitação. Tente novamente.');
      },
    });
  }

  openEditModal(): void {
    const cr = this.change();
    if (!cr) return;
    this.editForm = {
      title: cr.title,
      description: cr.description,
      changeType: cr.changeType,
      justification: cr.justification,
    };
    this.showEditModal.set(true);
  }

  closeEditModal(): void {
    this.showEditModal.set(false);
  }

  saveEdit(): void {
    const cr = this.change();
    if (!cr) return;
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.changeRequestService.updateChange(cr.id, this.editForm).subscribe({
      next: (updated) => {
        this.change.update((c) => (c ? { ...c, ...updated } : c));
        this.actionLoading.set(false);
        this.showEditModal.set(false);
      },
      error: () => {
        this.actionLoading.set(false);
        this.actionError.set('Erro ao salvar alterações.');
      },
    });
  }

  openReviewModal(): void {
    this.reviewImpact.set('');
    this.reviewRecommend.set(true);
    this.showReviewModal.set(true);
  }

  closeReviewModal(): void {
    this.showReviewModal.set(false);
  }

  submitReview(): void {
    const cr = this.change();
    if (!cr) return;
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.changeRequestService
      .reviewChange(cr.id, {
        impactAssessment: this.reviewImpact(),
        recommendApproval: this.reviewRecommend(),
      })
      .subscribe({
        next: (updated) => {
          this.change.update((c) => (c ? { ...c, ...updated } : c));
          this.actionLoading.set(false);
          this.showReviewModal.set(false);
        },
        error: () => {
          this.actionLoading.set(false);
          this.actionError.set('Erro ao enviar revisão.');
        },
      });
  }

  approveChange(): void {
    const cr = this.change();
    if (!cr) return;
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.changeRequestService.approveChange(cr.id, { approved: true }).subscribe({
      next: (updated) => {
        this.change.update((c) => (c ? { ...c, ...updated } : c));
        this.actionLoading.set(false);
      },
      error: () => {
        this.actionLoading.set(false);
        this.actionError.set('Erro ao aprovar solicitação.');
      },
    });
  }

  openRejectModal(): void {
    this.rejectionReason.set('');
    this.showRejectModal.set(true);
  }

  closeRejectModal(): void {
    this.showRejectModal.set(false);
  }

  submitRejection(): void {
    const cr = this.change();
    if (!cr) return;
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.changeRequestService
      .approveChange(cr.id, { approved: false, rejectionReason: this.rejectionReason() })
      .subscribe({
        next: (updated) => {
          this.change.update((c) => (c ? { ...c, ...updated } : c));
          this.actionLoading.set(false);
          this.showRejectModal.set(false);
        },
        error: () => {
          this.actionLoading.set(false);
          this.actionError.set('Erro ao rejeitar solicitação.');
        },
      });
  }

  implementChange(): void {
    const cr = this.change();
    if (!cr) return;
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.changeRequestService.implementChange(cr.id).subscribe({
      next: (updated) => {
        this.change.update((c) => (c ? { ...c, ...updated } : c));
        this.actionLoading.set(false);
      },
      error: () => {
        this.actionLoading.set(false);
        this.actionError.set('Erro ao marcar como implementado.');
      },
    });
  }

  openAddLinkModal(): void {
    this.newLink = { entityType: 'NON_CONFORMANCE', entityId: '', linkNote: '' };
    this.showAddLinkModal.set(true);
  }

  closeAddLinkModal(): void {
    this.showAddLinkModal.set(false);
  }

  addLink(): void {
    const cr = this.change();
    if (!cr || !this.newLink.entityId) return;
    this.actionLoading.set(true);
    this.changeRequestService
      .addLink(cr.id, {
        entityType: this.newLink.entityType,
        entityId: this.newLink.entityId,
        linkNote: this.newLink.linkNote || undefined,
      })
      .subscribe({
        next: (link) => {
          this.change.update((c) =>
            c ? { ...c, links: [...c.links, link] } : c,
          );
          this.actionLoading.set(false);
          this.showAddLinkModal.set(false);
        },
        error: () => {
          this.actionLoading.set(false);
          this.actionError.set('Erro ao adicionar vínculo.');
        },
      });
  }

  removeLink(linkId: string): void {
    const cr = this.change();
    if (!cr) return;
    this.changeRequestService.removeLink(cr.id, linkId).subscribe({
      next: () => {
        this.change.update((c) =>
          c ? { ...c, links: c.links.filter((l) => l.id !== linkId) } : c,
        );
      },
      error: () => {
        this.actionError.set('Erro ao remover vínculo.');
      },
    });
  }

  formatDate(date: string | undefined): string {
    if (!date) return '—';
    return new Date(date).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
