import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { interval, startWith, Subscription, switchMap } from 'rxjs';
import { KpiService, KpiSummaryResponse } from '../kpi.service';

const REFRESH_INTERVAL_MS = 300_000;

@Component({
  selector: 'app-kpi-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  templateUrl: './kpi-dashboard.component.html',
  styleUrl: './kpi-dashboard.component.scss',
})
export class KpiDashboardComponent implements OnInit, OnDestroy {
  private readonly kpiService = inject(KpiService);

  kpi = signal<KpiSummaryResponse | null>(null);
  loading = signal(true);
  errorMsg = signal<string | null>(null);

  private refreshSub?: Subscription;

  ngOnInit(): void {
    this.refreshSub = interval(REFRESH_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.kpiService.getSummary()),
      )
      .subscribe({
        next: (data) => {
          this.kpi.set(data);
          this.loading.set(false);
          this.errorMsg.set(null);
        },
        error: () => {
          this.loading.set(false);
          this.errorMsg.set('Não foi possível carregar os indicadores. Tente novamente.');
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
