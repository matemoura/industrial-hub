import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { AnalyticsService, MttrTrendResponse, WoSummaryResponse } from '../analytics.service';
import { LineChartComponent } from '../../shared/charts/line-chart/line-chart.component';
import { DoughnutChartComponent } from '../../shared/charts/doughnut-chart/doughnut-chart.component';

@Component({
  selector: 'app-maintenance-analytics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LineChartComponent, DoughnutChartComponent],
  templateUrl: './maintenance-analytics.component.html',
  styleUrl: './maintenance-analytics.component.scss',
})
export class MaintenanceAnalyticsComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);

  readonly monthOptions = [3, 6, 12, 18, 24];

  selectedMonths = signal(6);
  loadingMttr = signal(false);
  loadingWo = signal(false);
  errorMttr = signal<string | null>(null);
  errorWo = signal<string | null>(null);

  mttrData = signal<MttrTrendResponse | null>(null);
  woData = signal<WoSummaryResponse | null>(null);

  // Line chart — MTTR
  readonly mttrLabels = computed(() => this.mttrData()?.monthLabels ?? []);
  readonly mttrValues = computed(() => this.mttrData()?.mttrValues ?? []);
  readonly hasMttr = computed(() => (this.mttrData()?.monthLabels ?? []).length > 0);

  // Doughnut — by status
  readonly statusLabels = computed(() => Object.keys(this.woData()?.byStatus ?? {}));
  readonly statusValues = computed(() => Object.values(this.woData()?.byStatus ?? {}));
  readonly statusColors = computed(() =>
    this.statusLabels().map((s) => this.statusColor(s)),
  );

  // Doughnut — by type
  readonly typeLabels = computed(() => Object.keys(this.woData()?.byType ?? {}));
  readonly typeValues = computed(() => Object.values(this.woData()?.byType ?? {}));
  readonly typeColors = computed(() =>
    this.typeLabels().map((_, i) =>
      ['#0099B8', '#006B82', '#00C4E8', '#38A169', '#DD6B20'][i % 5],
    ),
  );

  readonly hasWo = computed(
    () =>
      Object.keys(this.woData()?.byStatus ?? {}).length > 0 ||
      Object.keys(this.woData()?.byType ?? {}).length > 0,
  );

  ngOnInit(): void {
    this.loadMttr();
    this.loadWo();
  }

  onMonthsChange(event: Event): void {
    this.selectedMonths.set(Number((event.target as HTMLSelectElement).value));
    this.loadMttr();
  }

  private loadMttr(): void {
    this.loadingMttr.set(true);
    this.errorMttr.set(null);
    this.analyticsService.getMttrTrend(this.selectedMonths()).subscribe({
      next: (res) => {
        this.mttrData.set(res);
        this.loadingMttr.set(false);
      },
      error: () => {
        this.errorMttr.set('Erro ao carregar dados de MTTR.');
        this.loadingMttr.set(false);
      },
    });
  }

  private loadWo(): void {
    this.loadingWo.set(true);
    this.errorWo.set(null);
    this.analyticsService.getWoSummary().subscribe({
      next: (res) => {
        this.woData.set(res);
        this.loadingWo.set(false);
      },
      error: () => {
        this.errorWo.set('Erro ao carregar resumo de ordens de serviço.');
        this.loadingWo.set(false);
      },
    });
  }

  private statusColor(status: string): string {
    const map: Record<string, string> = {
      OPEN: '#0099B8',
      IN_PROGRESS: '#DD6B20',
      DONE: '#38A169',
      CANCELLED: '#718096',
    };
    return map[status] ?? '#0099B8';
  }
}
