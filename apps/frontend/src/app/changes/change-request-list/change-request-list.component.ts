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
  ChangeRequestService,
  ChangeRequest,
  ChangeStatus,
  ChangeType,
  CreateChangeRequest,
  PageResponse,
} from '../change-request.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-change-request-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './change-request-list.component.html',
  styleUrl: './change-request-list.component.scss',
})
export class ChangeRequestListComponent implements OnInit {
  private readonly changeRequestService = inject(ChangeRequestService);
  private readonly authService = inject(AuthService);

  readonly role = computed(() => this.authService.role());
  readonly isSupervisor = computed(() => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN');
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly currentUsername = computed(() => this.authService.username() ?? '');

  changes = signal<ChangeRequest[]>([]);
  loading = signal(false);
  totalElements = signal(0);
  page = signal(0);
  pageSize = signal(20);

  // Summary counts
  draftCount = signal(0);
  submittedCount = signal(0);
  underReviewCount = signal(0);
  approvedCount = signal(0);
  implementedCount = signal(0);

  // Filters
  statusFilter = '';
  typeFilter = '';

  // Modal state
  showNewModal = signal(false);
  modalLoading = signal(false);
  modalError = signal<string | null>(null);

  newChange: CreateChangeRequest = {
    title: '',
    description: '',
    changeType: 'PROCESS',
    justification: '',
  };

  readonly statusColors: Record<ChangeStatus, string> = {
    DRAFT: '#818286',
    SUBMITTED: '#56A4BB',
    UNDER_REVIEW: '#E8A93C',
    APPROVED: '#3FA66A',
    REJECTED: '#D24A4A',
    IMPLEMENTED: '#1F3A4A',
  };

  readonly statusLabels: Record<ChangeStatus, string> = {
    DRAFT: 'Rascunho',
    SUBMITTED: 'Submetida',
    UNDER_REVIEW: 'Em Revisão',
    APPROVED: 'Aprovada',
    REJECTED: 'Rejeitada',
    IMPLEMENTED: 'Implementada',
  };

  readonly typeLabels: Record<ChangeType, string> = {
    PROCESS: 'Processo',
    DOCUMENT: 'Documento',
    EQUIPMENT: 'Equipamento',
    SOFTWARE: 'Software',
    REGULATORY: 'Regulatório',
    OTHER: 'Outro',
  };

  ngOnInit(): void {
    this.loadSummary();
    this.loadChanges();
  }

  private loadSummary(): void {
    const statuses: ChangeStatus[] = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'IMPLEMENTED'];
    for (const status of statuses) {
      this.changeRequestService.listChanges({ status, page: 0 }).subscribe({
        next: (p: PageResponse<ChangeRequest>) => {
          switch (status) {
            case 'DRAFT': this.draftCount.set(p.totalElements); break;
            case 'SUBMITTED': this.submittedCount.set(p.totalElements); break;
            case 'UNDER_REVIEW': this.underReviewCount.set(p.totalElements); break;
            case 'APPROVED': this.approvedCount.set(p.totalElements); break;
            case 'IMPLEMENTED': this.implementedCount.set(p.totalElements); break;
          }
        },
        error: () => {},
      });
    }
  }

  loadChanges(): void {
    this.loading.set(true);
    this.changeRequestService
      .listChanges({
        status: this.statusFilter as ChangeStatus | undefined || undefined,
        changeType: this.typeFilter as ChangeType | undefined || undefined,
        page: this.page(),
      })
      .subscribe({
        next: (p: PageResponse<ChangeRequest>) => {
          this.changes.set(p.content);
          this.totalElements.set(p.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onFilterChange(): void {
    this.page.set(0);
    this.loadChanges();
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.loadChanges();
    }
  }

  nextPage(): void {
    const maxPage = Math.ceil(this.totalElements() / this.pageSize()) - 1;
    if (this.page() < maxPage) {
      this.page.update((p) => p + 1);
      this.loadChanges();
    }
  }

  openNewModal(): void {
    this.newChange = { title: '', description: '', changeType: 'PROCESS', justification: '' };
    this.modalError.set(null);
    this.showNewModal.set(true);
  }

  closeNewModal(): void {
    this.showNewModal.set(false);
  }

  submitNewChange(): void {
    if (!this.newChange.title || !this.newChange.description || !this.newChange.justification) {
      this.modalError.set('Preencha todos os campos obrigatórios.');
      return;
    }
    this.modalLoading.set(true);
    this.modalError.set(null);
    this.changeRequestService.createChange(this.newChange).subscribe({
      next: () => {
        this.modalLoading.set(false);
        this.showNewModal.set(false);
        this.loadSummary();
        this.loadChanges();
      },
      error: () => {
        this.modalLoading.set(false);
        this.modalError.set('Erro ao criar solicitação. Tente novamente.');
      },
    });
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('pt-BR');
  }

  readonly Math = Math;
}
