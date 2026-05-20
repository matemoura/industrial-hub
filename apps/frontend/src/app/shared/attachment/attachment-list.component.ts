import {
  ChangeDetectionStrategy, Component, DestroyRef,
  computed, inject, input, OnInit, signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AttachmentService, AttachmentResponse } from './attachment.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-attachment-list',
  standalone: true,
  imports: [],
  templateUrl: './attachment-list.component.html',
  styleUrls: ['./attachment-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttachmentListComponent implements OnInit {
  entityType = input.required<string>();
  entityId = input.required<string>();

  private readonly attachmentService = inject(AttachmentService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  attachments = signal<AttachmentResponse[]>([]);
  isLoading = signal(false);
  isUploading = signal(false);
  thumbUrls = signal<Record<string, string>>({});

  canUpload = computed(() => {
    const r = this.authService.role();
    return r === 'OPERATOR' || r === 'SUPERVISOR' || r === 'ADMIN';
  });

  canDelete = computed(() => {
    const r = this.authService.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  ngOnInit(): void {
    this.loadAttachments();
  }

  loadAttachments(): void {
    this.isLoading.set(true);
    this.attachmentService
      .list(this.entityType(), this.entityId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.attachments.set(list);
          this.isLoading.set(false);
          this.loadThumbs(list);
        },
        error: () => this.isLoading.set(false),
      });
  }

  private loadThumbs(list: AttachmentResponse[]): void {
    list
      .filter((a) => a.contentType.startsWith('image/'))
      .forEach((a) => {
        this.attachmentService
          .getDownloadUrl(a.id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: ({ url }) =>
              this.thumbUrls.update((prev) => ({ ...prev, [a.id]: url })),
            error: () => {
              // URL generation failed — thumbnail not shown, no fatal error
            },
          });
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];

    const ALLOWED = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel'];
    const MAX_SIZE = 10 * 1024 * 1024;

    if (!ALLOWED.includes(file.type)) {
      alert('Tipo de arquivo não permitido. Use JPG, PNG, WebP, PDF ou Excel.');
      input.value = '';
      return;
    }
    if (file.size > MAX_SIZE) {
      alert('Arquivo excede o tamanho máximo de 10 MB.');
      input.value = '';
      return;
    }

    this.isUploading.set(true);
    this.attachmentService
      .upload(this.entityType(), this.entityId(), file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (attachment) => {
          this.attachments.update((prev) => [attachment, ...prev]);
          this.isUploading.set(false);
          input.value = '';
          if (attachment.contentType.startsWith('image/')) {
            this.attachmentService
              .getDownloadUrl(attachment.id)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({
                next: ({ url }) =>
                  this.thumbUrls.update((prev) => ({ ...prev, [attachment.id]: url })),
                error: () => {},
              });
          }
        },
        error: () => this.isUploading.set(false),
      });
  }

  deleteAttachment(id: string): void {
    if (!confirm('Remover este anexo?')) return;
    this.attachmentService
      .delete(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.attachments.update((prev) => prev.filter((a) => a.id !== id));
          this.thumbUrls.update((prev) => {
            const copy = { ...prev };
            delete copy[id];
            return copy;
          });
        },
      });
  }

  openUrl(id: string): void {
    this.attachmentService
      .getDownloadUrl(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: ({ url }) => window.open(url, '_blank', 'noopener,noreferrer') });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  isImage(a: AttachmentResponse): boolean {
    return a.contentType.startsWith('image/');
  }
}
