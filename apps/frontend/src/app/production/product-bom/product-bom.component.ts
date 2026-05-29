import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../auth/auth.service';
import { BomComponentRow, BomImportResponse, ProductionService } from '../production.service';

/**
 * US-101 — Componente standalone que exibe e permite importar o BOM de um produto.
 * Usado na página de detalhe de produto (/production/products/:code).
 */
@Component({
  selector: 'app-product-bom',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './product-bom.component.html',
  styleUrl: './product-bom.component.scss',
})
export class ProductBomComponent implements OnInit {
  readonly productCode = input.required<string>();

  private readonly service = inject(ProductionService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly role = this.auth.role;
  readonly isAdmin = () => this.role() === 'ADMIN';

  readonly components = signal<BomComponentRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Upload state
  readonly selectedFile = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly uploadResult = signal<BomImportResponse | null>(null);
  readonly uploadError = signal<string | null>(null);

  get templateUrl(): string {
    return this.service.getBomTemplateUrl();
  }

  ngOnInit(): void {
    this.loadBom();
  }

  loadBom(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getProductBom(this.productCode())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.components.set(rows);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao carregar estrutura BOM.');
          this.loading.set(false);
        },
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.uploadResult.set(null);
    this.uploadError.set(null);
  }

  uploadBom(): void {
    const file = this.selectedFile();
    if (!file) return;
    this.uploading.set(true);
    this.uploadResult.set(null);
    this.uploadError.set(null);

    this.service
      .importBom(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.uploadResult.set(result);
          this.uploading.set(false);
          this.selectedFile.set(null);
          this.loadBom(); // reload BOM table
        },
        error: () => {
          this.uploadError.set('Erro ao importar BOM. Verifique o arquivo e tente novamente.');
          this.uploading.set(false);
        },
      });
  }

  typeLabel(type: string): string {
    const map: Record<string, string> = {
      FINISHED: 'Produto Acabado',
      INTERMEDIATE: 'Intermediário',
      RAW_MATERIAL: 'Matéria-Prima',
    };
    return map[type] ?? type;
  }
}
