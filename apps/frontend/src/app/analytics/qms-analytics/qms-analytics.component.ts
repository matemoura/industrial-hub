import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { AnalyticsService, NcParetoResponse, NcTrendResponse } from '../analytics.service';
import { BarChartComponent } from '../../shared/charts/bar-chart/bar-chart.component';
import { LineChartComponent } from '../../shared/charts/line-chart/line-chart.component';

@Component({
  selector: 'app-qms-analytics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BarChartComponent, LineChartComponent],
  templateUrl: './qms-analytics.component.html',
  styleUrl: './qms-analytics.component.scss',
})
export class QmsAnalyticsComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);

  readonly paretoPeriodOptions = [30, 90, 180];
  readonly trendWeekOptions = [4, 8, 12, 26, 52];

  selectedDays = signal(90);
  selectedWeeks = signal(12);

  loadingPareto = signal(false);
  loadingTrend = signal(false);
  errorPareto = signal<string | null>(null);
  errorTrend = signal<string | null>(null);

  paretoData = signal<NcParetoResponse | null>(null);
  trendData = signal<NcTrendResponse | null>(null);

  // Bar chart — by type
  readonly typeLabels = computed(() => Object.keys(this.paretoData()?.byType ?? {}));
  readonly typeValues = computed(() => Object.values(this.paretoData()?.byType ?? {}));

  // Bar chart — by severity
  readonly severityLabels = computed(() => Object.keys(this.paretoData()?.bySeverity ?? {}));
  readonly severityValues = computed(() => Object.values(this.paretoData()?.bySeverity ?? {}));
  readonly severityColors = computed(() =>
    this.severityLabels().map((s) => this.severityColor(s)),
  );

  // Line chart — trend
  readonly trendLabels = computed(() => this.trendData()?.labels ?? []);
  readonly trendValues = computed(() => this.trendData()?.values ?? []);

  readonly hasPareto = computed(
    () =>
      Object.keys(this.paretoData()?.byType ?? {}).length > 0 ||
      Object.keys(this.paretoData()?.bySeverity ?? {}).length > 0,
  );
  readonly hasTrend = computed(() => (this.trendData()?.labels ?? []).length > 0);

  ngOnInit(): void {
    this.loadPareto();
    this.loadTrend();
  }

  onDaysChange(event: Event): void {
    this.selectedDays.set(Number((event.target as HTMLSelectElement).value));
    this.loadPareto();
  }

  onWeeksChange(event: Event): void {
    this.selectedWeeks.set(Number((event.target as HTMLSelectElement).value));
    this.loadTrend();
  }

  private loadPareto(): void {
    this.loadingPareto.set(true);
    this.errorPareto.set(null);
    this.analyticsService.getNcPareto(this.selectedDays()).subscribe({
      next: (res) => {
        this.paretoData.set(res);
        this.loadingPareto.set(false);
      },
      error: () => {
        this.errorPareto.set('Erro ao carregar dados de NCs.');
        this.loadingPareto.set(false);
      },
    });
  }

  private loadTrend(): void {
    this.loadingTrend.set(true);
    this.errorTrend.set(null);
    this.analyticsService.getNcTrend(this.selectedWeeks()).subscribe({
      next: (res) => {
        this.trendData.set(res);
        this.loadingTrend.set(false);
      },
      error: () => {
        this.errorTrend.set('Erro ao carregar tendência de NCs.');
        this.loadingTrend.set(false);
      },
    });
  }

  private severityColor(severity: string): string {
    const map: Record<string, string> = {
      CRITICAL: '#EF4444',
      HIGH: '#F97316',
      MEDIUM: '#EAB308',
      LOW: '#22C55E',
    };
    return map[severity] ?? '#0099B8';
  }
}
