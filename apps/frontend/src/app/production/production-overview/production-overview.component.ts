import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EfficiencyChartComponent } from './efficiency-chart.component';
import {
  DailyEfficiencyDto,
  ProductionOverviewDto,
  ProductionService,
} from '../production.service';

@Component({
  selector: 'app-production-overview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EfficiencyChartComponent],
  templateUrl: './production-overview.component.html',
  styleUrl: './production-overview.component.scss',
})
export class ProductionOverviewComponent implements OnInit {
  private readonly service = inject(ProductionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly overview = signal<ProductionOverviewDto | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Computed helpers para o template
  readonly opStatusEntries = computed(() => {
    const ops = this.overview()?.opsByStatus ?? {};
    return Object.entries(ops).sort((a, b) => b[1] - a[1]);
  });

  readonly trendRows = computed<DailyEfficiencyDto[]>(
    () => this.overview()?.efficiencyTrend ?? [],
  );

  /** AC-3: formato esperado pelo NgxCharts LineChartComponent */
  readonly chartData = computed(() => [{
    name: 'Eficiência',
    series: this.trendRows().map(r => ({
      name: r.date,
      value: r.avgEfficiency,
    })),
  }]);


  ngOnInit(): void {
    this.service
      .getProductionOverview()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.overview.set(data);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao carregar painel. Tente novamente.');
          this.loading.set(false);
        },
      });
  }

  formatPct(value: number | null | undefined): string {
    return value !== null && value !== undefined ? `${value.toFixed(1)}%` : '—';
  }

  formatStatus(status: string): string {
    const map: Record<string, string> = {
      PLANNED: 'Planejada',
      RELEASED: 'Liberada',
      IN_PROGRESS: 'Em andamento',
      DONE: 'Concluída',
      CANCELLED: 'Cancelada',
    };
    return map[status] ?? status;
  }
}
