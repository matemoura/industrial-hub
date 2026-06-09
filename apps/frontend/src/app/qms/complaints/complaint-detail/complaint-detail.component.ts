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
  ComplaintService,
  Complaint,
  ComplaintStatus,
  ComplaintSource,
  NcSeverity,
} from '../../complaints.service';
import { AuthService } from '../../../auth/auth.service';

@Component({
  selector: 'app-complaint-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './complaint-detail.component.html',
  styleUrl: './complaint-detail.component.scss',
})
export class ComplaintDetailComponent implements OnInit {
  private readonly complaintService = inject(ComplaintService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  complaint = signal<Complaint | null>(null);
  loading = signal(false);
  isSaving = signal(false);
  isGeneratingMdr = signal(false);
  showAnvisaModal = signal(false);
  saveError = signal<string | null>(null);
  snackbarVisible = signal(false);

  // Edit form state
  editInvestigationSummary = '';
  editRootCause = '';
  editCorrectiveAction = '';

  // ANVISA modal form
  anvisaReportNumber = '';
  anvisaReportDate = '';

  // Link NC/CAPA inputs
  linkNcId = '';
  linkCapaId = '';
  showLinkNcInput = signal(false);
  showLinkCapaInput = signal(false);

  readonly statusColors: Record<ComplaintStatus, string> = {
    RECEIVED: '#818286',
    UNDER_INVESTIGATION: '#56A4BB',
    INVESTIGATION_COMPLETED: '#E8A93C',
    CLOSED: '#3FA66A',
  };

  readonly statusLabels: Record<ComplaintStatus, string> = {
    RECEIVED: 'Recebida',
    UNDER_INVESTIGATION: 'Em Investigação',
    INVESTIGATION_COMPLETED: 'Investigação Concluída',
    CLOSED: 'Encerrada',
  };

  readonly sourceLabels: Record<ComplaintSource, string> = {
    CLIENT: 'Cliente',
    DISTRIBUTOR: 'Distribuidor',
    REGULATORY_BODY: 'Órgão Regulatório',
    INTERNAL: 'Interna',
  };

  readonly severityColors: Record<NcSeverity, string> = {
    LOW: '#3FA66A',
    MEDIUM: '#E8A93C',
    HIGH: '#D24A4A',
    CRITICAL: '#9C0000',
  };

  readonly statusSteps: ComplaintStatus[] = [
    'RECEIVED',
    'UNDER_INVESTIGATION',
    'INVESTIGATION_COMPLETED',
    'CLOSED',
  ];

  readonly canGenerateMdr = computed(() => {
    const c = this.complaint();
    return this.isAdmin() && !!c?.reportedToAnvisa && c?.status === 'CLOSED';
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadComplaint(id);
  }

  private loadComplaint(id: string): void {
    this.loading.set(true);
    this.complaintService.getComplaint(id).subscribe({
      next: (c) => {
        this.complaint.set(c);
        this.editInvestigationSummary = c.investigationSummary ?? '';
        this.editRootCause = c.rootCause ?? '';
        this.editCorrectiveAction = c.correctiveAction ?? '';
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  stepIndex(status: ComplaintStatus): number {
    return this.statusSteps.indexOf(status);
  }

  isStepDone(step: ComplaintStatus): boolean {
    const current = this.complaint()?.status;
    if (!current) return false;
    return this.stepIndex(step) <= this.stepIndex(current);
  }

  nextStatus(): ComplaintStatus | null {
    const current = this.complaint()?.status;
    if (!current || current === 'CLOSED') return null;
    const idx = this.stepIndex(current);
    return this.statusSteps[idx + 1] ?? null;
  }

  transitionLabel(): string {
    const next = this.nextStatus();
    if (!next) return '';
    const labels: Record<ComplaintStatus, string> = {
      RECEIVED: '',
      UNDER_INVESTIGATION: 'Iniciar Investigação',
      INVESTIGATION_COMPLETED: 'Concluir Investigação',
      CLOSED: 'Fechar Reclamação',
    };
    return labels[next] ?? '';
  }

  transition(): void {
    const c = this.complaint();
    const next = this.nextStatus();
    if (!c || !next) return;
    this.complaintService.updateStatus(c.id, { status: next }).subscribe({
      next: (updated) => {
        this.complaint.set(updated);
        this.editInvestigationSummary = updated.investigationSummary ?? '';
        this.editRootCause = updated.rootCause ?? '';
        this.editCorrectiveAction = updated.correctiveAction ?? '';
      },
      error: () => {},
    });
  }

  saveInvestigation(): void {
    const c = this.complaint();
    if (!c) return;
    this.isSaving.set(true);
    this.saveError.set(null);
    this.complaintService.updateComplaint(c.id, {
      investigationSummary: this.editInvestigationSummary,
      rootCause: this.editRootCause,
      correctiveAction: this.editCorrectiveAction,
    }).subscribe({
      next: (updated) => {
        this.complaint.set(updated);
        this.isSaving.set(false);
      },
      error: () => {
        this.saveError.set('Erro ao salvar. Tente novamente.');
        this.isSaving.set(false);
      },
    });
  }

  submitLinkNc(): void {
    const c = this.complaint();
    if (!c || !this.linkNcId.trim()) return;
    this.complaintService.linkNc(c.id, this.linkNcId.trim()).subscribe({
      next: (updated) => {
        this.complaint.set(updated);
        this.linkNcId = '';
        this.showLinkNcInput.set(false);
      },
      error: () => {},
    });
  }

  submitLinkCapa(): void {
    const c = this.complaint();
    if (!c || !this.linkCapaId.trim()) return;
    this.complaintService.linkCapa(c.id, this.linkCapaId.trim()).subscribe({
      next: (updated) => {
        this.complaint.set(updated);
        this.linkCapaId = '';
        this.showLinkCapaInput.set(false);
      },
      error: () => {},
    });
  }

  openAnvisaModal(): void {
    this.anvisaReportNumber = '';
    this.anvisaReportDate = '';
    this.showAnvisaModal.set(true);
  }

  closeAnvisaModal(): void {
    this.showAnvisaModal.set(false);
  }

  submitAnvisaReport(): void {
    const c = this.complaint();
    if (!c || !this.anvisaReportNumber || !this.anvisaReportDate) return;
    this.complaintService.reportAnvisa(c.id, {
      reportNumber: this.anvisaReportNumber,
      reportDate: this.anvisaReportDate,
    }).subscribe({
      next: (updated) => {
        this.complaint.set(updated);
        this.showAnvisaModal.set(false);
      },
      error: () => {},
    });
  }

  generateMdr(): void {
    const c = this.complaint();
    if (!c) return;
    this.isGeneratingMdr.set(true);
    this.complaintService.generateMdrReport(c.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `mdr-${c.code}-${c.reportedDate}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.isGeneratingMdr.set(false);
        this.showSnackbar();
      },
      error: () => this.isGeneratingMdr.set(false),
    });
  }

  private showSnackbar(): void {
    this.snackbarVisible.set(true);
    setTimeout(() => this.snackbarVisible.set(false), 3000);
  }

  formatDate(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString('pt-BR');
  }
}
