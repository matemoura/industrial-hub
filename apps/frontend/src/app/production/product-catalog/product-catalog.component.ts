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
  ImportResult,
  Product,
  ProductFamily,
  ProductionService,
} from '../production.service';

@Component({
  selector: 'app-product-catalog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './product-catalog.component.html',
  styleUrl: './product-catalog.component.scss',
})
export class ProductCatalogComponent implements OnInit {
  private readonly service = inject(ProductionService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly role = this.auth.role;
  readonly canImport = computed(
    () => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN',
  );

  readonly isLoading = signal(true);
  readonly products = signal<Product[]>([]);
  readonly families = signal<ProductFamily[]>([]);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);

  // Filters
  readonly filterFamilyId = signal<string>('');
  readonly filterActive = signal<boolean | null>(null);

  // Upload panel
  readonly uploadExpanded = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly isUploading = signal(false);
  readonly importResult = signal<ImportResult | null>(null);
  readonly uploadError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadFamilies();
    this.loadProducts();
  }

  private loadFamilies(): void {
    this.service
      .listFamilies()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.families.set(data),
        error: () => this.errorMsg.set('Erro ao carregar famílias.'),
      });
  }

  loadProducts(): void {
    this.isLoading.set(true);
    this.errorMsg.set(null);
    const active = this.filterActive();
    this.service
      .listProducts({
        familyId: this.filterFamilyId() || undefined,
        active: active != null ? active : undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.products.set(page.content);
          this.isLoading.set(false);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar produtos.');
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
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.importResult.set(null);
    this.uploadError.set(null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0] ?? null;
    if (file) {
      this.selectedFile.set(file);
      this.importResult.set(null);
      this.uploadError.set(null);
    }
  }

  confirmUpload(): void {
    const file = this.selectedFile();
    if (!file || this.isUploading()) return;
    this.isUploading.set(true);
    this.uploadError.set(null);
    this.service
      .importProducts(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.importResult.set(result);
          this.isUploading.set(false);
          this.loadProducts();
        },
        error: (err: { error?: { message?: string } }) => {
          this.uploadError.set(err?.error?.message ?? 'Erro ao importar arquivo.');
          this.isUploading.set(false);
        },
      });
  }

  readonly skeletonRows = [1, 2, 3];
}
