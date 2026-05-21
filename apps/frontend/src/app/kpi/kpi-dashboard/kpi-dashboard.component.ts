import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EMPTY, Subscription, catchError, interval, startWith, switchMap } from 'rxjs';
import { KpiService, KpiSummaryResponse } from '../kpi.service';
import { SlaService, SlaSummaryResponse } from '../../admin/sla-rules/sla.service';

const REFRESH_INTERVAL_MS = 300_000;

@Component({
  selector: 'app-kpi-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './kpi-dashboard.component.html',
  styleUrl: './kpi-dashboard.component.scss',
})
export class KpiDashboardComponent implements OnInit, OnDestroy {
  private readonly kpiService = inject(KpiService);
  private readonly slaService = inject(SlaService);

  kpi = signal<KpiSummaryResponse | null>(null);
  loading = signal(true);
  errorMsg = signal<string | null>(null);

  slaSummary = signal<SlaSummaryResponse | null>(null);
  slaLoading = signal(true);

  readonly showSlaPanel = computed(() => {
    const s = this.slaSummary();
    if (!s) return false;
    return s.totalBreachedNcs > 0 || s.totalBreachedWorkOrders > 0;
  });

  private refreshSub?: Subscription;

  ngOnInit(): void {
    this.refreshSub = interval(REFRESH_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.kpiService.getSummary().pipe(
            catchError(() => {
              this.loading.set(false);
              this.errorMsg.set('Não foi possível carregar os indicadores. Tente novamente.');
              return EMPTY;
            }),
          ),
        ),
      )
      .subscribe((data) => {
        this.kpi.set(data);
        this.loading.set(false);
        this.errorMsg.set(null);
      });

    this.slaService.getSlaSummary().subscribe({
      next: (s) => {
        this.slaSummary.set(s);
        this.slaLoading.set(false);
      },
      error: () => {
        this.slaLoading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
  }

  formatOee(value: number | null): string {
    if (value === null) return 'N/A';
    return (value * 100).toFixed(1) + '%';
  }

  oeeClass(value: number | null): string {
    if (value === null) return '';
    return value < 0.65 ? 'card--danger' : 'card--ok';
  }

  criticalClass(count: number): string {
    return count > 0 ? 'card--danger' : 'card--ok';
  }
}
