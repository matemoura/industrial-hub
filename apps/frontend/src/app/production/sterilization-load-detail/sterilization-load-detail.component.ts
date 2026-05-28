import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../auth/auth.service';
import {
  LoadStatus,
  SterilizationLoadsService,
  SterilizationLoadDetail,
  SterilizationMethod,
} from '../sterilization-loads.service';

@Component({
  selector: 'app-sterilization-load-detail',
  standalone: true,
  imports: [RouterLink, SlicePipe],
  templateUrl: './sterilization-load-detail.component.html',
  styleUrl: './sterilization-load-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SterilizationLoadDetailComponent implements OnInit {
  private readonly service = inject(SterilizationLoadsService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly load = signal<SterilizationLoadDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly transitioning = signal(false);
  readonly transitionError = signal<string | null>(null);
  readonly confirmTransitionTo = signal<LoadStatus | null>(null);
  readonly removingOrderId = signal<string | null>(null);
  readonly showReleasedReminder = signal(false);
  private pendingReloadId: string | null = null;

  readonly isSupervisor = computed(() => {
    const r = this.auth.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  readonly allowedTransitions = computed((): LoadStatus[] => {
    const status = this.load()?.status;
    if (!status) return [];
    const map: Partial<Record<LoadStatus, LoadStatus[]>> = {
      OPEN: ['CLOSED'],
      CLOSED: ['STERILIZING'],
      STERILIZING: ['RELEASED', 'REJECTED'],
    };
    return map[status] ?? [];
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadDetail(id);
  }

  loadDetail(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getLoad(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.load.set(detail);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao carregar detalhes da carga.');
          this.loading.set(false);
        },
      });
  }

  requestTransition(target: LoadStatus): void {
    this.confirmTransitionTo.set(target);
  }

  cancelTransition(): void {
    this.confirmTransitionTo.set(null);
  }

  dismissReleasedReminder(): void {
    this.showReleasedReminder.set(false);
    if (this.pendingReloadId) {
      this.loadDetail(this.pendingReloadId);
      this.pendingReloadId = null;
    }
  }

  confirmTransition(): void {
    const target = this.confirmTransitionTo();
    const loadId = this.load()?.id;
    if (!target || !loadId) return;
    this.transitioning.set(true);
    this.transitionError.set(null);
    this.service
      .transitionStatus(loadId, target)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.confirmTransitionTo.set(null);
          this.transitioning.set(false);
          if (target === 'RELEASED') {
            this.pendingReloadId = loadId;
            this.showReleasedReminder.set(true);
          } else {
            this.loadDetail(loadId);
          }
        },
        error: (err) => {
          this.transitionError.set(err.error?.message ?? 'Erro ao alterar status.');
          this.transitioning.set(false);
          this.confirmTransitionTo.set(null);
        },
      });
  }

  requestRemoveOrder(orderId: string): void {
    this.removingOrderId.set(orderId);
  }

  cancelRemoveOrder(): void {
    this.removingOrderId.set(null);
  }

  confirmRemoveOrder(): void {
    const orderId = this.removingOrderId();
    const loadId = this.load()?.id;
    if (!orderId || !loadId) return;
    this.service
      .removeOrder(loadId, orderId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.removingOrderId.set(null);
          this.loadDetail(loadId);
        },
        error: (err) => {
          this.error.set(err.error?.message ?? 'Erro ao remover OP.');
          this.removingOrderId.set(null);
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

  transitionLabel(target: LoadStatus): string {
    const map: Record<LoadStatus, string> = {
      OPEN: 'Reabrir',
      CLOSED: 'Fechar Carga',
      STERILIZING: 'Iniciar Esterilização',
      RELEASED: 'Liberar',
      REJECTED: 'Rejeitar',
    };
    return map[target] ?? target;
  }
}
