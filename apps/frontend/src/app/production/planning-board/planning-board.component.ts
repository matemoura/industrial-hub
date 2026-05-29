import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../auth/auth.service';
import {
  FamilyPlanningBoard,
  MrpRunResult,
  MrpSuggestion,
  PlanningService,
  PurchaseNeed,
} from '../planning.service';

@Component({
  selector: 'app-planning-board',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './planning-board.component.html',
  styleUrl: './planning-board.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanningBoardComponent implements OnInit, OnDestroy {
  private readonly service = inject(PlanningService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly board = signal<FamilyPlanningBoard[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // MRP dry-run modal state
  readonly showMrpModal = signal(false);
  readonly mrpDryRunResult = signal<MrpRunResult | null>(null);
  readonly mrpRunning = signal(false);
  readonly mrpError = signal<string | null>(null);

  // adjustedQtys for the modal (id → qty)
  readonly adjustedQtys = signal<Record<string, number>>({});

  // Purchase needs
  readonly purchaseNeeds = signal<PurchaseNeed[]>([]);
  readonly purchaseNeedsError = signal(false);

  // Expanded families
  readonly expandedFamilies = signal<Set<string>>(new Set());

  // Toast after run
  readonly toast = signal<string | null>(null);
  // SEC-118: guard timeout ID for cleanup on destroy
  private toastTimeoutId: ReturnType<typeof setTimeout> | null = null;

  readonly isSupervisor = computed(() => {
    const r = this.auth.role();
    return r === 'SUPERVISOR' || r === 'ADMIN';
  });

  ngOnInit(): void {
    this.loadBoard();
    this.loadPurchaseNeeds();
  }

  loadBoard(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getPlanningBoard()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.board.set(data);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao carregar board de planejamento.');
          this.loading.set(false);
        },
      });
  }

  loadPurchaseNeeds(): void {
    this.service
      .getPurchaseNeeds()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.purchaseNeeds.set(data),
        error: () => this.purchaseNeedsError.set(true),
      });
  }

  toggleFamily(familyCode: string): void {
    this.expandedFamilies.update((set) => {
      const next = new Set(set);
      if (next.has(familyCode)) next.delete(familyCode);
      else next.add(familyCode);
      return next;
    });
  }

  isFamilyExpanded(familyCode: string): boolean {
    return this.expandedFamilies().has(familyCode);
  }

  // ---- MRP flow ----

  executeMrp(): void {
    if (!this.isSupervisor()) return;
    this.mrpRunning.set(true);
    this.mrpError.set(null);
    this.service
      .dryRunMrp()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.mrpDryRunResult.set(result);
          // Init adjustedQtys with suggestedQty per suggestion
          const qtys: Record<string, number> = {};
          result.suggestions.forEach((s) => { qtys[s.id] = s.suggestedQty; });
          this.adjustedQtys.set(qtys);
          this.mrpRunning.set(false);
          this.showMrpModal.set(true);
        },
        error: () => {
          this.mrpError.set('Erro ao executar simulação MRP.');
          this.mrpRunning.set(false);
        },
      });
  }

  cancelMrp(): void {
    this.showMrpModal.set(false);
    this.mrpDryRunResult.set(null);
  }

  confirmMrp(): void {
    this.mrpRunning.set(true);
    this.service
      .runMrp()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.showMrpModal.set(false);
          this.mrpRunning.set(false);
          this.mrpDryRunResult.set(null);
          this.showToast(`${result.suggestions.length} sugestões geradas`);
          this.loadBoard();
          this.loadPurchaseNeeds();
        },
        error: () => {
          this.mrpError.set('Erro ao executar MRP.');
          this.mrpRunning.set(false);
        },
      });
  }

  updateAdjustedQty(suggestionId: string, qty: number): void {
    this.adjustedQtys.update((qtys) => ({ ...qtys, [suggestionId]: qty }));
  }

  private showToast(msg: string): void {
    this.toast.set(msg);
    // SEC-118: cancel any previous timeout before scheduling a new one
    if (this.toastTimeoutId !== null) clearTimeout(this.toastTimeoutId);
    this.toastTimeoutId = setTimeout(() => {
      this.toast.set(null);
      this.toastTimeoutId = null;
    }, 4000);
  }

  ngOnDestroy(): void {
    // SEC-118: prevent callback firing after component is destroyed
    if (this.toastTimeoutId !== null) clearTimeout(this.toastTimeoutId);
  }

  planningStatusLabel(status: string): string {
    const map: Record<string, string> = { OK: 'OK', ALERT: 'Atenção', CRITICAL: 'Crítico' };
    return map[status] ?? status;
  }
}
