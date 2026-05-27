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
  ProductionOrder,
  ProductionService,
  StockSnapshot,
} from '../production.service';

type ActiveTab = 'stock' | 'orders';

@Component({
  selector: 'app-production-orders',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './production-orders.component.html',
  styleUrl: './production-orders.component.scss',
})
export class ProductionOrdersComponent implements OnInit {
  private readonly service = inject(ProductionService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly role = this.auth.role;
  readonly canImport = computed(
    () => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN',
  );

  readonly activeTab = signal<ActiveTab>('stock');

  // Stock state
  readonly stockItems = signal<StockSnapshot[]>([]);
  readonly isLoadingStock = signal(true);
  readonly filterProductStock = signal('');

  // Orders state
  readonly orders = signal<ProductionOrder[]>([]);
  readonly isLoadingOrders = signal(true);
  readonly filterStatus = signal('');
  readonly ordersTotalPages = signal(0);
  readonly ordersPage = signal(0);

  // Upload panel — stock
  readonly stockUploadExpanded = signal(false);
  readonly stockSelectedFile = signal<File | null>(null);
  readonly stockIsUploading = signal(false);
  readonly stockImportResult = signal<ImportResult | null>(null);
  readonly stockUploadError = signal<string | null>(null);

  // Upload panel — orders
  readonly ordersUploadExpanded = signal(false);
  readonly ordersSelectedFile = signal<File | null>(null);
  readonly ordersIsUploading = signal(false);
  readonly ordersImportResult = signal<ImportResult | null>(null);
  readonly ordersUploadError = signal<string | null>(null);

  readonly errorMsg = signal<string | null>(null);

  readonly skeletonRows = [1, 2, 3];

  readonly statusOptions = [
    { value: '', label: 'Todos os status' },
    { value: 'PLANNED', label: 'Planejada' },
    { value: 'IN_PROGRESS', label: 'Em andamento' },
    { value: 'COMPLETED', label: 'Concluída' },
    { value: 'CANCELLED', label: 'Cancelada' },
  ];

  ngOnInit(): void {
    this.loadStock();
    this.loadOrders();
  }

  setTab(tab: ActiveTab): void {
    this.activeTab.set(tab);
  }

  // ── Stock ────────────────────────────────────────────────────────────────

  loadStock(): void {
    this.isLoadingStock.set(true);
    this.service
      .listStock({
        productId: this.filterProductStock() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.stockItems.set(data);
          this.isLoadingStock.set(false);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar estoque.');
          this.isLoadingStock.set(false);
        },
      });
  }

  toggleStockUpload(): void {
    this.stockUploadExpanded.update((v) => !v);
    if (!this.stockUploadExpanded()) {
      this.stockSelectedFile.set(null);
      this.stockImportResult.set(null);
      this.stockUploadError.set(null);
    }
  }

  onStockFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.stockSelectedFile.set(input.files?.[0] ?? null);
    this.stockImportResult.set(null);
    this.stockUploadError.set(null);
  }

  onStockDragOver(event: DragEvent): void { event.preventDefault(); }
  onStockDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0] ?? null;
    if (file) {
      this.stockSelectedFile.set(file);
      this.stockImportResult.set(null);
    }
  }

  confirmStockUpload(): void {
    const file = this.stockSelectedFile();
    if (!file || this.stockIsUploading()) return;
    this.stockIsUploading.set(true);
    this.stockUploadError.set(null);
    this.service
      .importStock(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.stockImportResult.set(result);
          this.stockIsUploading.set(false);
          this.loadStock();
        },
        error: (err: { error?: { message?: string } }) => {
          this.stockUploadError.set(err?.error?.message ?? 'Erro ao importar estoque.');
          this.stockIsUploading.set(false);
        },
      });
  }

  // ── Orders ───────────────────────────────────────────────────────────────

  loadOrders(): void {
    this.isLoadingOrders.set(true);
    this.service
      .listOrders({
        status: this.filterStatus() || undefined,
        page: this.ordersPage(),
        size: 20,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.orders.set(page.content);
          this.ordersTotalPages.set(page.totalPages);
          this.isLoadingOrders.set(false);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar ordens de produção.');
          this.isLoadingOrders.set(false);
        },
      });
  }

  goToPage(page: number): void {
    this.ordersPage.set(page);
    this.loadOrders();
  }

  toggleOrdersUpload(): void {
    this.ordersUploadExpanded.update((v) => !v);
    if (!this.ordersUploadExpanded()) {
      this.ordersSelectedFile.set(null);
      this.ordersImportResult.set(null);
      this.ordersUploadError.set(null);
    }
  }

  onOrdersFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.ordersSelectedFile.set(input.files?.[0] ?? null);
    this.ordersImportResult.set(null);
    this.ordersUploadError.set(null);
  }

  onOrdersDragOver(event: DragEvent): void { event.preventDefault(); }
  onOrdersDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0] ?? null;
    if (file) {
      this.ordersSelectedFile.set(file);
      this.ordersImportResult.set(null);
    }
  }

  confirmOrdersUpload(): void {
    const file = this.ordersSelectedFile();
    if (!file || this.ordersIsUploading()) return;
    this.ordersIsUploading.set(true);
    this.ordersUploadError.set(null);
    this.service
      .importOrders(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.ordersImportResult.set(result);
          this.ordersIsUploading.set(false);
          this.loadOrders();
        },
        error: (err: { error?: { message?: string } }) => {
          this.ordersUploadError.set(err?.error?.message ?? 'Erro ao importar ordens.');
          this.ordersIsUploading.set(false);
        },
      });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PLANNED': return 'Planejada';
      case 'IN_PROGRESS': return 'Em andamento';
      case 'COMPLETED': return 'Concluída';
      case 'CANCELLED': return 'Cancelada';
      default: return status;
    }
  }

  statusChipClass(status: string): string {
    switch (status) {
      case 'PLANNED': return 'chip chip--grey';
      case 'IN_PROGRESS': return 'chip chip--blue';
      case 'COMPLETED': return 'chip chip--green';
      case 'CANCELLED': return 'chip chip--red';
      default: return 'chip';
    }
  }

  readonly pageRange = computed(() => {
    const total = this.ordersTotalPages();
    return Array.from({ length: total }, (_, i) => i);
  });
}
