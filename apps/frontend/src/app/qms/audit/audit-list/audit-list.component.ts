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
  AuditService,
  InternalAudit,
  AuditStatus,
  AuditType,
  AuditComplianceDashboard,
  CreateAuditRequest,
  PageResponse,
} from '../../audit.service';
import { AuthService } from '../../../auth/auth.service';

@Component({
  selector: 'app-audit-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './audit-list.component.html',
  styleUrl: './audit-list.component.scss',
})
export class AuditListComponent implements OnInit {
  private readonly auditService = inject(AuditService);
  private readonly authService = inject(AuthService);

  readonly isSupervisor = computed(() => {
    const role = this.authService.role();
    return role === 'SUPERVISOR' || role === 'ADMIN';
  });

  audits = signal<InternalAudit[]>([]);
  dashboard = signal<AuditComplianceDashboard | null>(null);
  loading = signal(false);
  totalElements = signal(0);
  page = signal(0);
  pageSize = signal(20);

  // Filters (two-way bound via ngModel)
  statusFilter = '';
  typeFilter = '';

  // New-audit modal state
  showNewModal = signal(false);
  modalLoading = signal(false);
  modalError = signal<string | null>(null);

  // New-audit form model
  newAudit = {
  title: '',
  scope: '',
  auditType: 'INTERNAL' as AuditType,
  plannedDate: '',
  leadAuditor: '',
  auditees: '',
  };

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

  readonly typeLabels: Record<AuditType, string> = {
    INTERNAL: 'Interna',
    SUPPLIER: 'Fornecedor',
    PROCESS: 'Processo',
    SYSTEM: 'Sistema',
  };

  ngOnInit(): void {
    this.loadDashboard();
    this.loadAudits();
  }

  private loadDashboard(): void {
    this.auditService.getComplianceDashboard().subscribe({
      next: (d) => this.dashboard.set(d),
    });
  }

  loadAudits(): void {
    this.loading.set(true);
    this.auditService
      .listAudits({
        status: this.statusFilter as AuditStatus | undefined || undefined,
        auditType: this.typeFilter as AuditType | undefined || undefined,
        page: this.page(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (p: PageResponse<InternalAudit>) => {
          this.audits.set(p.content);
          this.totalElements.set(p.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onFilterChange(): void {
    this.page.set(0);
    this.loadAudits();
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.loadAudits();
    }
  }

  nextPage(): void {
    const maxPage = Math.ceil(this.totalElements() / this.pageSize()) - 1;
    if (this.page() < maxPage) {
      this.page.update((p) => p + 1);
      this.loadAudits();
    }
  }

  openNewModal(): void {
    this.newAudit = { title: '', scope: '', auditType: 'INTERNAL', plannedDate: '', leadAuditor: '', auditees: '' };
    this.modalError.set(null);
    this.showNewModal.set(true);
  }

  closeNewModal(): void {
    this.showNewModal.set(false);
  }

  submitNewAudit(): void {
    if (!this.newAudit.title || !this.newAudit.scope || !this.newAudit.plannedDate || !this.newAudit.leadAuditor) {
      this.modalError.set('Preencha todos os campos obrigatórios.');
      return;
    }
    this.modalLoading.set(true);
    this.modalError.set(null);
    const req: CreateAuditRequest = {
      title: this.newAudit.title,
      scope: this.newAudit.scope,
      auditType: this.newAudit.auditType,
      plannedDate: this.newAudit.plannedDate,
      leadAuditor: this.newAudit.leadAuditor,
      auditees: this.newAudit.auditees
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    };
    this.auditService.createAudit(req).subscribe({
      next: () => {
        this.modalLoading.set(false);
        this.showNewModal.set(false);
        this.loadDashboard();
        this.loadAudits();
      },
      error: () => {
        this.modalLoading.set(false);
        this.modalError.set('Erro ao criar auditoria. Tente novamente.');
      },
    });
  }

  formatDate(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString('pt-BR');
  }
}
