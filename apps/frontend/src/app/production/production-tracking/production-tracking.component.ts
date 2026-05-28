import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { interval, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../auth/auth.service';
import {
  FamilyTrackingResponse,
  OrderTrackingEntry,
  ProductionOrderDisplayStatus,
  ProductionTrackingService,
  ProductionTrackingSummaryResponse,
  ProductType,
} from '../production-tracking.service';

const STATUS_LABELS: Record<ProductionOrderDisplayStatus, string> = {
  PLANNED: 'Planejada',
  RELEASED: 'Liberada',
  IN_PROGRESS: 'Em Produção',
  PENDING_STERILIZATION: 'Aguard. Esterilização',
  IN_LOAD: 'Em Carga',
  STERILIZING: 'Esterilizando',
  DONE: 'Concluída',
};

const ALL_COLUMNS: ProductionOrderDisplayStatus[] = [
  'PLANNED',
  'RELEASED',
  'IN_PROGRESS',
  'PENDING_STERILIZATION',
  'IN_LOAD',
  'STERILIZING',
  'DONE',
];

@Component({
  selector: 'app-production-tracking',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './production-tracking.component.html',
  styleUrl: './production-tracking.component.scss',
})
export class ProductionTrackingComponent implements OnInit {
  private readonly trackingService = inject(ProductionTrackingService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly role = this.auth.role;
  readonly canRefresh = computed(
    () => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN',
  );

  // Data signals
  readonly families = signal<FamilyTrackingResponse[]>([]);
  readonly summary = signal<ProductionTrackingSummaryResponse | null>(null);
  readonly lastSyncAt = signal<string | null>(null);

  // Loading/error states
  readonly isLoading = signal(true);
  readonly isRefreshing = signal(false);
  readonly errorMsg = signal<string | null>(null);

  // Filters
  readonly selectedFamilyCode = signal<string | null>(null);
  readonly filterProductType = signal<ProductType | 'ALL'>('ALL');
  readonly filterOnlyOverdue = signal(false);

  // Side panel
  readonly selectedOrder = signal<OrderTrackingEntry | null>(null);
  readonly panelOpen = signal(false);

  // Column definitions
  readonly allColumns = ALL_COLUMNS;
  readonly statusLabels = STATUS_LABELS;

  // Derived: families visible as chips
  readonly familyChips = computed(() => this.families());

  // Derived: filtered orders from selected family or all families
  readonly filteredOrders = computed<OrderTrackingEntry[]>(() => {
    const selectedCode = this.selectedFamilyCode();
    const productType = this.filterProductType();
    const onlyOverdue = this.filterOnlyOverdue();

    let orders: OrderTrackingEntry[] = [];
    const fams = this.families();

    if (selectedCode == null) {
      orders = fams.flatMap((f) => f.orders);
    } else {
      const fam = fams.find((f) => f.familyCode === selectedCode);
      orders = fam ? fam.orders : [];
    }

    if (productType !== 'ALL') {
      orders = orders.filter((o) => o.productType === productType);
    }

    if (onlyOverdue) {
      orders = orders.filter((o) => o.overdue);
    }

    return orders;
  });

  // Whether any FINISHED type orders exist (controls sterilization columns visibility)
  readonly hasFinishedOrders = computed(() =>
    this.filteredOrders().some((o) => o.productType === 'FINISHED'),
  );

  // Columns to show (hide sterilization columns if no FINISHED orders)
  readonly visibleColumns = computed<ProductionOrderDisplayStatus[]>(() => {
    const hasFinished = this.hasFinishedOrders();
    return ALL_COLUMNS.filter((col) => {
      if (col === 'PENDING_STERILIZATION' || col === 'IN_LOAD') {
        return hasFinished;
      }
      return true;
    });
  });

  // Orders per column
  columnOrders(status: ProductionOrderDisplayStatus): OrderTrackingEntry[] {
    return this.filteredOrders().filter((o) => o.displayStatus === status);
  }

  // Formatted lastSyncAt
  readonly lastSyncAgo = computed<string>(() => {
    const raw = this.lastSyncAt();
    if (!raw) return 'Nunca sincronizado';

    const syncDate = new Date(raw);
    if (isNaN(syncDate.getTime())) return 'Nunca sincronizado';

    const diffMs = Date.now() - syncDate.getTime();
    const diffMins = Math.floor(diffMs / 60_000);

    if (diffMins < 1) return 'há menos de 1 minuto';
    if (diffMins < 60) return `há ${diffMins} ${diffMins === 1 ? 'minuto' : 'minutos'}`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `há ${diffHours} ${diffHours === 1 ? 'hora' : 'horas'}`;

    const diffDays = Math.floor(diffHours / 24);
    return `há ${diffDays} ${diffDays === 1 ? 'dia' : 'dias'}`;
  });

  // Skeleton rows for loading state
  readonly skeletonColumns = [1, 2, 3, 4];
  readonly skeletonCards = [1, 2, 3];

  ngOnInit(): void {
    this.loadSummary();
    this.startAutoRefresh();
  }

  private startAutoRefresh(): void {
    // Initial load + auto-refresh every 5 minutes via RxJS interval
    interval(300_000)
      .pipe(
        startWith(0),
        switchMap(() => {
          this.isRefreshing.set(true);
          return this.trackingService.getFamilies();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (data) => {
          this.families.set(data);
          const maxSync = data.reduce<string | null>((acc, f) => {
            if (!f.lastSyncAt) return acc;
            if (!acc) return f.lastSyncAt;
            return f.lastSyncAt > acc ? f.lastSyncAt : acc;
          }, null);
          this.lastSyncAt.set(maxSync);
          this.isLoading.set(false);
          this.isRefreshing.set(false);
          this.errorMsg.set(null);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar dados de produção. Tente novamente.');
          this.isLoading.set(false);
          this.isRefreshing.set(false);
        },
      });
  }

  private loadSummary(): void {
    this.trackingService
      .getSummary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (s) => {
          this.summary.set(s);
          if (s.lastSyncAt && (!this.lastSyncAt() || s.lastSyncAt > (this.lastSyncAt() ?? ''))) {
            this.lastSyncAt.set(s.lastSyncAt);
          }
        },
        error: () => {
          // summary is non-critical, silently ignore
        },
      });
  }

  loadTracking(): void {
    this.isLoading.set(true);
    this.errorMsg.set(null);
    this.trackingService
      .getFamilies()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.families.set(data);
          const maxSync = data.reduce<string | null>((acc, f) => {
            if (!f.lastSyncAt) return acc;
            if (!acc) return f.lastSyncAt;
            return f.lastSyncAt > acc ? f.lastSyncAt : acc;
          }, null);
          this.lastSyncAt.set(maxSync);
          this.isLoading.set(false);
          this.errorMsg.set(null);
        },
        error: () => {
          this.errorMsg.set('Erro ao carregar dados de produção. Tente novamente.');
          this.isLoading.set(false);
        },
      });
    this.loadSummary();
  }

  selectFamily(code: string | null): void {
    this.selectedFamilyCode.set(code);
  }

  openPanel(order: OrderTrackingEntry): void {
    this.selectedOrder.set(order);
    this.panelOpen.set(true);
  }

  closePanel(): void {
    this.panelOpen.set(false);
    this.selectedOrder.set(null);
  }

  formatDate(dateStr: string): string {
    const d = new Date(dateStr);
    return d.toLocaleDateString('pt-BR');
  }

  truncate(text: string, maxLen = 25): string {
    return text.length > maxLen ? text.slice(0, maxLen) + '…' : text;
  }

  statusClass(status: ProductionOrderDisplayStatus): string {
    return `status-${status.toLowerCase().replace(/_/g, '-')}`;
  }

  formatPct(value: number | null): string {
    if (value === null) return '—';
    return Math.round(value).toString();
  }
}
