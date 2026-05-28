import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../auth/auth.service';
import {
  LoadStatus,
  SterilizationLoadsService,
  SterilizationLoadSummary,
  PendingOrder,
  CreateLoadBody,
  SterilizationMethod,
} from '../sterilization-loads.service';

@Component({
  selector: 'app-sterilization-loads',
  standalone: true,
  imports: [RouterLink, SlicePipe],
  templateUrl: './sterilization-loads.component.html',
  styleUrl: './sterilization-loads.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SterilizationLoadsComponent implements OnInit {
  private readonly service = inject(SterilizationLoadsService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loads = signal<SterilizationLoadSummary[]>([]);
  readonly pendingOrders = signal<PendingOrder[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly filterStatus = signal<LoadStatus | ''>('');
  readonly showCreateForm = signal(false);
  readonly addingOrderToLoadId = signal<string | null>(null);
  readonly confirmingOrderId = signal<string | null>(null);

  readonly isSupervisor = computed(() => {
    const r = this.auth.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  readonly loadsByStatus = computed(() => {
    const all = this.loads();
    const filter = this.filterStatus();
    const filtered = filter ? all.filter((l) => l.status === filter) : all;
    const groups: Partial<Record<LoadStatus, SterilizationLoadSummary[]>> = {};
    for (const load of filtered) {
      if (!groups[load.status]) groups[load.status] = [];
      groups[load.status]!.push(load);
    }
    return groups;
  });

  readonly statusOrder: LoadStatus[] = ['OPEN', 'CLOSED', 'STERILIZING', 'RELEASED', 'REJECTED'];

  // Create form state
  readonly newMethod = signal<SterilizationMethod | ''>('');
  readonly newNotes = signal('');
  readonly creating = signal(false);

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .listLoads()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.loads.set(page.content);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao carregar cargas de esterilização.');
          this.loading.set(false);
        },
      });
  }

  openAllocationPanel(loadId: string): void {
    this.addingOrderToLoadId.set(loadId);
    this.service
      .getPendingOrders()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (orders) => this.pendingOrders.set(orders) });
  }

  closeAllocationPanel(): void {
    this.addingOrderToLoadId.set(null);
    this.confirmingOrderId.set(null);
    this.pendingOrders.set([]);
  }

  requestAddOrder(orderId: string): void {
    this.confirmingOrderId.set(orderId);
  }

  confirmAddOrder(): void {
    const loadId = this.addingOrderToLoadId();
    const orderId = this.confirmingOrderId();
    if (!loadId || !orderId) return;
    this.service
      .addOrder(loadId, orderId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.closeAllocationPanel();
          this.loadData();
        },
        error: (err) => {
          this.error.set(err.error?.message ?? 'Erro ao alocar OP.');
          this.confirmingOrderId.set(null);
        },
      });
  }

  cancelConfirm(): void {
    this.confirmingOrderId.set(null);
  }

  createLoad(): void {
    const method = this.newMethod();
    if (!method) return;
    const body: CreateLoadBody = { method, notes: this.newNotes() || undefined };
    this.creating.set(true);
    this.service
      .createLoad(body)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.showCreateForm.set(false);
          this.newMethod.set('');
          this.newNotes.set('');
          this.creating.set(false);
          this.loadData();
        },
        error: () => {
          this.error.set('Erro ao criar carga.');
          this.creating.set(false);
        },
      });
  }

  statusLabel(status: LoadStatus): string {
    const map: Record<LoadStatus, string> = {
      OPEN: 'Aberta',
      CLOSED: 'Fechada',
      STERILIZING: 'Esterilizando',
      RELEASED: 'Liberada',
      REJECTED: 'Rejeitada',
    };
    return map[status] ?? status;
  }

  methodLabel(method: SterilizationMethod | null): string {
    if (!method) return '—';
    const map: Record<SterilizationMethod, string> = {
      EO_GAS: 'Óxido de Etileno',
      GAMMA: 'Radiação Gama',
      STEAM: 'Vapor',
      OTHER: 'Outro',
    };
    return map[method] ?? method;
  }
}
