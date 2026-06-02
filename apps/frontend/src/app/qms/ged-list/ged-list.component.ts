import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  GedService,
  DocumentSummary,
  DocumentCategory,
  DocumentStatus,
  Page,
} from '../ged.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-ged-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule],
  templateUrl: './ged-list.component.html',
  styleUrl: './ged-list.component.scss',
})
export class GedListComponent implements OnInit {
  private readonly gedService = inject(GedService);
  private readonly authService = inject(AuthService);

  readonly role = this.authService.role;

  page = signal<Page<DocumentSummary> | null>(null);
  loading = signal(true);
  showCreateModal = signal(false);
  createLoading = signal(false);
  createError = signal<string | null>(null);

  filterCategory = signal<DocumentCategory | ''>('');
  filterStatus = signal<DocumentStatus | ''>('');

  newCode = signal('');
  newTitle = signal('');
  newCategory = signal<DocumentCategory | ''>('');
  newChangeReason = signal('');
  selectedFile = signal<File | null>(null);

  readonly categories: DocumentCategory[] = [
    'SOP',
    'FORM',
    'POLICY',
    'WORK_INSTRUCTION',
    'RECORD',
  ];
  readonly statuses: DocumentStatus[] = ['DRAFT', 'PUBLISHED', 'OBSOLETE'];

  readonly categoryLabels: Record<DocumentCategory, string> = {
    SOP: 'SOP',
    FORM: 'Formulário',
    POLICY: 'Política',
    WORK_INSTRUCTION: 'Instrução de Trabalho',
    RECORD: 'Registro',
  };

  readonly statusLabels: Record<DocumentStatus, string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicado',
    OBSOLETE: 'Obsoleto',
  };

  readonly categoryColors: Record<DocumentCategory, string> = {
    SOP: '#5F88A1',
    FORM: '#818286',
    POLICY: '#7B5EA7',
    WORK_INSTRUCTION: '#E8A93C',
    RECORD: '#3FA66A',
  };

  readonly statusColors: Record<DocumentStatus, string> = {
    DRAFT: '#E8A93C',
    PUBLISHED: '#3FA66A',
    OBSOLETE: '#818286',
  };

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  ngOnInit(): void {
    this.loadList(0);
  }

  loadList(pageIndex: number): void {
    this.loading.set(true);
    this.gedService
      .listDocuments({
        category: this.filterCategory() || undefined,
        status: this.filterStatus() || undefined,
        page: pageIndex,
      })
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  applyFilters(): void {
    this.loadList(0);
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.createError.set(null);
    this.newCode.set('');
    this.newTitle.set('');
    this.newCategory.set('');
    this.newChangeReason.set('');
    this.selectedFile.set(null);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
  }

  submitCreate(): void {
    const file = this.selectedFile();
    if (!file) return;

    const category = this.newCategory();
    if (!category) {
      this.createError.set('Selecione uma categoria.');
      return;
    }

    this.createLoading.set(true);
    this.createError.set(null);

    const data = {
      code: this.newCode(),
      title: this.newTitle(),
      category,
      changeReason: this.newChangeReason(),
    };

    const form = new FormData();
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    form.append('file', file);

    this.gedService.createDocument(form).subscribe({
      next: () => {
        this.createLoading.set(false);
        this.closeCreateModal();
        this.loadList(0);
      },
      error: (err) => {
        this.createLoading.set(false);
        this.createError.set(err?.error?.message ?? 'Erro ao criar documento.');
      },
    });
  }

  goToPage(index: number): void {
    this.loadList(index);
  }

  formatDate(iso: string): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('pt-BR');
  }
}
