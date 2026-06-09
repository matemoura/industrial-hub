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
  ComplaintService,
  Complaint,
  ComplaintIndicators,
  ComplaintStatus,
  ComplaintSource,
  NcSeverity,
  CreateComplaintRequest,
} from '../../complaints.service';
import { AuthService } from '../../../auth/auth.service';

@Component({
  selector: 'app-complaint-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './complaint-list.component.html',
  styleUrl: './complaint-list.component.scss',
})
export class ComplaintListComponent implements OnInit {
  private readonly complaintService = inject(ComplaintService);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  complaints = signal<Complaint[]>([]);
  indicators = signal<ComplaintIndicators | null>(null);
  loading = signal(false);
  totalElements = signal(0);
  page = signal(0);
  readonly pageSize = 20;

  statusFilter = signal<ComplaintStatus | ''>('');
  severityFilter = signal<NcSeverity | ''>('');
  productFilter = signal('');
  onlyNotReported = signal(false);
  showCreateModal = signal(false);
  createError = signal<string | null>(null);
  createLoading = signal(false);

  newComplaint: CreateComplaintRequest = {
    title: '',
    description: '',
    source: 'CLIENT',
    productCode: '',
    batchNumber: '',
    severity: 'LOW',
    reportedDate: '',
    reportedBy: '',
    assignedTo: '',
  };

  readonly notReportedCount = computed(() => {
    const ind = this.indicators();
    if (!ind) return 0;
    return Math.max(0, ind.totalReceived - ind.reportedToAnvisa);
  });

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

  ngOnInit(): void {
    const now = new Date();
    const to = now.toISOString().slice(0, 10);
    const fromDate = new Date(now);
    fromDate.setFullYear(fromDate.getFullYear() - 1);
    const from = fromDate.toISOString().slice(0, 10);
    this.loadIndicators(from, to);
    this.loadComplaints();
  }

  private loadIndicators(from: string, to: string): void {
    this.complaintService.getIndicators(from, to).subscribe({
      next: (ind) => this.indicators.set(ind),
      error: () => {},
    });
  }

  loadComplaints(): void {
    this.loading.set(true);
    const status = this.statusFilter() || undefined;
    const severity = this.severityFilter() || undefined;
    const productCode = this.productFilter() || undefined;
    const reportedToAnvisa = this.onlyNotReported() ? false : undefined;

    this.complaintService.listComplaints({
      status: status as ComplaintStatus | undefined,
      severity: severity as NcSeverity | undefined,
      productCode,
      reportedToAnvisa,
      page: this.page(),
    }).subscribe({
      next: (p) => {
        this.complaints.set(p.content);
        this.totalElements.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onFilterChange(): void {
    this.page.set(0);
    this.loadComplaints();
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.loadComplaints();
    }
  }

  nextPage(): void {
    const maxPage = Math.ceil(this.totalElements() / this.pageSize) - 1;
    if (this.page() < maxPage) {
      this.page.update((p) => p + 1);
      this.loadComplaints();
    }
  }

  openCreateModal(): void {
    this.newComplaint = {
      title: '',
      description: '',
      source: 'CLIENT',
      productCode: '',
      batchNumber: '',
      severity: 'LOW',
      reportedDate: '',
      reportedBy: '',
      assignedTo: '',
    };
    this.createError.set(null);
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
  }

  submitCreate(): void {
    if (!this.newComplaint.title || !this.newComplaint.description || !this.newComplaint.reportedDate || !this.newComplaint.reportedBy || !this.newComplaint.assignedTo) {
      this.createError.set('Preencha todos os campos obrigatórios.');
      return;
    }
    this.createLoading.set(true);
    this.createError.set(null);
    const req: CreateComplaintRequest = {
      ...this.newComplaint,
      productCode: this.newComplaint.productCode || undefined,
      batchNumber: this.newComplaint.batchNumber || undefined,
    };
    this.complaintService.createComplaint(req).subscribe({
      next: () => {
        this.createLoading.set(false);
        this.showCreateModal.set(false);
        this.page.set(0);
        this.loadComplaints();
      },
      error: () => {
        this.createLoading.set(false);
        this.createError.set('Erro ao criar reclamação. Tente novamente.');
      },
    });
  }

  formatDate(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString('pt-BR');
  }

  totalPages(): number {
    return Math.ceil(this.totalElements() / this.pageSize);
  }
}
