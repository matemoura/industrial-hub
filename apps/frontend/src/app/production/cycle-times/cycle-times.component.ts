import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../auth/auth.service';
import {
  CycleTime,
  ImportResult,
  Product,
  ProductionService,
} from '../production.service';

@Component({
  selector: 'app-cycle-times',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './cycle-times.component.html',
  styleUrl: './cycle-times.component.scss',
})
export class CycleTimesComponent implements OnInit {
  private readonly service = inject(ProductionService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly role = this.auth.role;
  readonly canImport = computed(
    () => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN',
  );

  readonly isLoading = signal(true);
  readonly cycleTimes = signal<CycleTime[]>([]);
  readonly products = signal<Product[]>([]);
  readonly errorMsg = signal<string | null>(null);

  readonly filterProductId = signal('');

  // Upload panel
  readonly uploadExpanded = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly isUploading = signal(false);
  readonly importResult = signal<ImportResult | null>(null);
  readonly uploadError = signal<string | null>(null);

  readonly skeletonRows = [1, 2, 3];

  ngOnInit(): void {
    this.loadProducts();
    this.loadCycleTimes();
  }

  private loadProducts(): void {
    this.service
      .listProducts({ size: 200 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.products.set(page.content),
        error: () => {},
      });
  }

  loadCycleTimes(): void {
    this.isLoading.set(true);
    this.errorMsg.set(null);
    this.service
      .listCycleTimes({
        productId: this.filterProductId() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.cycleTimes.set(data);
          this.isLoading.set(false);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar tempos de ciclo.');
          this.isLoading.set(false);
        },
      });
  }

  toggleUpload(): void {
    this.uploadExpanded.update((v) => !v);
    if (!this.uploadExpanded()) {
      this.selectedFile.set(null);
      this.importResult.set(null);
      this.uploadError.set(null);
    }
  }

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.importResult.set(null);
    this.uploadError.set(null);
  }

  onDragOver(event: DragEvent): void { event.preventDefault(); }
  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0] ?? null;
    if (file) {
      this.selectedFile.set(file);
      this.importResult.set(null);
    }
  }

  confirmUpload(): void {
    const file = this.selectedFile();
    if (!file || this.isUploading()) return;
    this.isUploading.set(true);
    this.uploadError.set(null);
    this.service
      .importCycleTimes(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.importResult.set(result);
          this.isUploading.set(false);
          this.loadCycleTimes();
        },
        error: (err: { error?: { message?: string } }) => {
          this.uploadError.set(err?.error?.message ?? 'Erro ao importar tempos de ciclo.');
          this.isUploading.set(false);
        },
      });
  }

  formatCycleTime(secs: number): string {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = secs % 60;
    const parts: string[] = [];
    if (h > 0) parts.push(`${h}h`);
    if (m > 0) parts.push(`${m}m`);
    if (s > 0 || parts.length === 0) parts.push(`${s}s`);
    return parts.join(' ');
  }
}
