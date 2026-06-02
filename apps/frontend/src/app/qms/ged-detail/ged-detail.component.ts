import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { GedService, DocumentDetail, DocumentStatus } from '../ged.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-ged-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './ged-detail.component.html',
  styleUrl: './ged-detail.component.scss',
})
export class GedDetailComponent implements OnInit {
  private readonly gedService = inject(GedService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly role = this.authService.role;

  doc = signal<DocumentDetail | null>(null);
  loading = signal(true);
  downloadingRevId = signal<string | null>(null);
  showAddRevisionModal = signal(false);
  addRevisionLoading = signal(false);
  addRevisionError = signal<string | null>(null);
  newRevisionFile = signal<File | null>(null);
  newRevisionReason = signal('');

  readonly statusLabels: Record<DocumentStatus, string> = {
    DRAFT: 'Rascunho',
    PUBLISHED: 'Publicado',
    OBSOLETE: 'Obsoleto',
  };

  readonly statusColors: Record<DocumentStatus, string> = {
    DRAFT: '#E8A93C',
    PUBLISHED: '#3FA66A',
    OBSOLETE: '#818286',
  };

  readonly categoryLabels: Record<string, string> = {
    SOP: 'SOP',
    FORM: 'Formulário',
    POLICY: 'Política',
    WORK_INSTRUCTION: 'Instrução de Trabalho',
    RECORD: 'Registro',
  };

  readonly categoryColors: Record<string, string> = {
    SOP: '#5F88A1',
    FORM: '#818286',
    POLICY: '#7B5EA7',
    WORK_INSTRUCTION: '#E8A93C',
    RECORD: '#3FA66A',
  };

  get isAdmin(): boolean {
    return this.role() === 'ADMIN';
  }

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  ngOnInit(): void {
    this.loadDocument();
  }

  loadDocument(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    this.loading.set(true);
    this.gedService.getDocument(id).subscribe({
      next: (data) => {
        this.doc.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  downloadRevision(revId: string): void {
    const docId = this.doc()?.id;
    if (!docId) return;
    this.downloadingRevId.set(revId);
    this.gedService.getDownloadUrl(docId, revId).subscribe({
      next: ({ url }) => {
        window.open(url, '_blank', 'noopener,noreferrer');
        this.downloadingRevId.set(null);
      },
      error: () => this.downloadingRevId.set(null),
    });
  }

  publish(): void {
    const docId = this.doc()?.id;
    if (!docId) return;
    this.gedService.updateStatus(docId, 'PUBLISHED').subscribe({
      next: (updated) => this.doc.set(updated),
    });
  }

  obsolete(): void {
    const docId = this.doc()?.id;
    if (!docId) return;
    this.gedService.updateStatus(docId, 'OBSOLETE').subscribe({
      next: (updated) => this.doc.set(updated),
    });
  }

  openAddRevisionModal(): void {
    this.showAddRevisionModal.set(true);
    this.addRevisionError.set(null);
    this.newRevisionFile.set(null);
    this.newRevisionReason.set('');
  }

  closeAddRevisionModal(): void {
    this.showAddRevisionModal.set(false);
    this.addRevisionError.set(null);
  }

  onRevisionFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.newRevisionFile.set(file);
  }

  submitAddRevision(): void {
    const file = this.newRevisionFile();
    const docId = this.doc()?.id;
    if (!file || !docId) return;

    this.addRevisionLoading.set(true);
    this.addRevisionError.set(null);

    const data = { changeReason: this.newRevisionReason() };
    const form = new FormData();
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    form.append('file', file);

    this.gedService.addRevision(docId, form).subscribe({
      next: () => {
        this.addRevisionLoading.set(false);
        this.closeAddRevisionModal();
        this.loadDocument();
      },
      error: (err) => {
        this.addRevisionLoading.set(false);
        this.addRevisionError.set(err?.error?.message ?? 'Erro ao adicionar revisão.');
      },
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  formatDate(iso: string): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('pt-BR');
  }

  formatDateTime(iso: string): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('pt-BR');
  }
}
