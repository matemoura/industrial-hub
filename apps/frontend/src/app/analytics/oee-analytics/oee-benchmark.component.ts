import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { catchError, of } from 'rxjs';
import {
  AnalyticsService,
  BenchmarkEntry,
  BenchmarkResponse,
  PeriodComparisonResponse,
} from '../analytics.service';
import { BarChartComponent } from '../../shared/charts/bar-chart/bar-chart.component';
import { LineChartComponent } from '../../shared/charts/line-chart/line-chart.component';

@Component({
  selector: 'app-oee-benchmark',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BarChartComponent, LineChartComponent, DecimalPipe],
  templateUrl: './oee-benchmark.component.html',
  styleUrl: './oee-benchmark.component.scss',
})
export class OeeBenchmarkComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);
  private readonly destroyRef = inject(DestroyRef);

  // ── Rankings ─────────────────────────────────────────────────────────────
  benchmarkWorkers = signal<BenchmarkEntry[]>([]);
  benchmarkShifts = signal<BenchmarkResponse | null>(null);
  benchmarkEquipment = signal<BenchmarkEntry[]>([]);

  // ── Period comparison ─────────────────────────────────────────────────────
  periodComparison = signal<PeriodComparisonResponse | null>(null);

  // ── Loading / UI state ────────────────────────────────────────────────────
  isBenchmarkLoading = signal(false);
  showWorldClassRef = signal(false);

  // ── Date pickers for rankings ─────────────────────────────────────────────
  benchmarkFrom = signal<string>('');
  benchmarkTo = signal<string>('');
  rankingError = signal<string | null>(null);

  // ── Date pickers for period comparison ────────────────────────────────────
  periodAFrom = signal<string>('');
  periodATo = signal<string>('');
  periodBFrom = signal<string>('');
  periodBTo = signal<string>('');
  comparisonError = signal<string | null>(null);
  isComparisonLoading = signal(false);

  // ── Error messages per section ────────────────────────────────────────────
  workersError = signal<string | null>(null);
  shiftsError = signal<string | null>(null);
  equipmentError = signal<string | null>(null);

  // ── Computed: OEE chip color thresholds ───────────────────────────────────
  readonly worldClassRefValue = computed(() => (this.showWorldClassRef() ? 85 : undefined));

  // ── Workers bar chart data ────────────────────────────────────────────────
  readonly workersLabels = computed(() => this.benchmarkWorkers().map((e) => e.label));
  readonly workersValues = computed(() =>
    this.benchmarkWorkers().map((e) => +(e.avgOee * 100).toFixed(1)),
  );
  readonly workersColors = computed(() => this.benchmarkWorkers().map((e) => oeeColor(e.avgOee)));

  // ── Shifts bar chart data ─────────────────────────────────────────────────
  readonly shiftsRanking = computed(() => this.benchmarkShifts()?.ranking ?? []);
  readonly shiftsLabels = computed(() => this.shiftsRanking().map((e) => e.label));
  readonly shiftsValues = computed(() =>
    this.shiftsRanking().map((e) => +(e.avgOee * 100).toFixed(1)),
  );
  readonly shiftsColors = computed(() => this.shiftsRanking().map((e) => oeeColor(e.avgOee)));
  readonly recordsWithoutShift = computed(
    () => this.benchmarkShifts()?.recordsWithoutShift ?? 0,
  );

  // ── Equipment bar chart data ───────────────────────────────────────────────
  readonly equipmentLabels = computed(() => this.benchmarkEquipment().map((e) => e.label));
  readonly equipmentValues = computed(() =>
    this.benchmarkEquipment().map((e) => +(e.avgOee * 100).toFixed(1)),
  );
  readonly equipmentColors = computed(() =>
    this.benchmarkEquipment().map((e) => oeeColor(e.avgOee)),
  );

  // ── Period comparison line chart data ─────────────────────────────────────
  readonly periodALabels = computed(() => {
    const c = this.periodComparison();
    return c ? c.periodA.map((e) => e.label) : [];
  });
  readonly periodBLabels = computed(() =>
    this.periodComparison()?.periodB?.map((b) => b.label) ?? [],
  );
  readonly periodAValues = computed(() => {
    const c = this.periodComparison();
    return c ? c.periodA.map((e) => +(e.avgOee * 100).toFixed(1)) : [];
  });
  readonly periodBValues = computed(() => {
    const c = this.periodComparison();
    return c ? c.periodB.map((e) => +(e.avgOee * 100).toFixed(1)) : [];
  });
  readonly improvementPct = computed(() => this.periodComparison()?.improvementPct ?? null);

  ngOnInit(): void {
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);

    this.benchmarkTo.set(formatDate(today));
    this.benchmarkFrom.set(formatDate(thirtyDaysAgo));
    this.periodATo.set(formatDate(today));
    this.periodAFrom.set(formatDate(thirtyDaysAgo));

    const sixtyDaysAgo = new Date();
    sixtyDaysAgo.setDate(today.getDate() - 60);
    const thirtyOneDaysAgo = new Date();
    thirtyOneDaysAgo.setDate(today.getDate() - 31);
    this.periodBFrom.set(formatDate(sixtyDaysAgo));
    this.periodBTo.set(formatDate(thirtyOneDaysAgo));

    this.loadRankings();
  }

  onBenchmarkFromChange(event: Event): void {
    this.benchmarkFrom.set((event.target as HTMLInputElement).value);
  }

  onBenchmarkToChange(event: Event): void {
    this.benchmarkTo.set((event.target as HTMLInputElement).value);
  }

  onUpdateRankings(): void {
    const err = this.validateRankingPeriod();
    if (err) {
      this.rankingError.set(err);
      return;
    }
    this.rankingError.set(null);
    this.loadRankings();
  }

  onToggleWorldClass(): void {
    this.showWorldClassRef.set(!this.showWorldClassRef());
  }

  onCompare(): void {
    const err = this.validateComparisonPeriods();
    if (err) {
      this.comparisonError.set(err);
      return;
    }
    this.comparisonError.set(null);
    this.loadComparison();
  }

  private validateRankingPeriod(): string | null {
    const from = this.benchmarkFrom();
    const to = this.benchmarkTo();
    if (!from || !to) return 'Preencha as datas de início e fim.';
    if (new Date(from) > new Date(to)) return 'A data inicial deve ser anterior à data final.';
    const diffDays = diffInDays(from, to);
    if (diffDays > 90) return 'O período máximo para benchmarking é 90 dias.';
    return null;
  }

  private validateComparisonPeriods(): string | null {
    const { aFrom, aTo, bFrom, bTo } = {
      aFrom: this.periodAFrom(),
      aTo: this.periodATo(),
      bFrom: this.periodBFrom(),
      bTo: this.periodBTo(),
    };
    if (!aFrom || !aTo || !bFrom || !bTo) return 'Preencha todas as datas dos dois períodos.';
    if (new Date(aFrom) > new Date(aTo)) return 'Período A: data inicial deve ser anterior à final.';
    if (new Date(bFrom) > new Date(bTo)) return 'Período B: data inicial deve ser anterior à final.';
    if (diffInDays(aFrom, aTo) > 90) return 'Período A: máximo de 90 dias.';
    if (diffInDays(bFrom, bTo) > 90) return 'Período B: máximo de 90 dias.';
    return null;
  }

  // ── Template helpers ──────────────────────────────────────────────────────

  oeeChipBg(avgOee: number): string {
    return oeeColor(avgOee);
  }

  getInputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  private loadRankings(): void {
    const from = this.benchmarkFrom();
    const to = this.benchmarkTo();
    this.isBenchmarkLoading.set(true);

    forkJoin({
      workers: this.analyticsService
        .getBenchmarkWorkers(from, to)
        .pipe(catchError(() => { this.workersError.set('Erro ao carregar workers.'); return of(null); })),
      shifts: this.analyticsService
        .getBenchmarkShifts(from, to)
        .pipe(catchError(() => { this.shiftsError.set('Erro ao carregar turnos.'); return of(null); })),
      equipment: this.analyticsService
        .getBenchmarkEquipmentType(from, to)
        .pipe(catchError(() => { this.equipmentError.set('Erro ao carregar equipamentos.'); return of(null); })),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((results) => {
        if (results.workers) {
          this.benchmarkWorkers.set(results.workers.ranking ?? []);
          this.workersError.set(null);
        }
        if (results.shifts) {
          this.benchmarkShifts.set(results.shifts);
          this.shiftsError.set(null);
        }
        if (results.equipment) {
          this.benchmarkEquipment.set(results.equipment.ranking ?? []);
          this.equipmentError.set(null);
        }
        this.isBenchmarkLoading.set(false);
      });
  }

  private loadComparison(): void {
    this.isComparisonLoading.set(true);
    this.analyticsService
      .getPeriodComparison(
        this.periodAFrom(),
        this.periodATo(),
        this.periodBFrom(),
        this.periodBTo(),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.periodComparison.set(res);
          this.isComparisonLoading.set(false);
        },
        error: () => {
          this.comparisonError.set('Erro ao carregar comparação de períodos.');
          this.isComparisonLoading.set(false);
        },
      });
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function oeeColor(avgOee: number): string {
  if (avgOee >= 0.65) return '#4CAF50';
  if (avgOee >= 0.5) return '#FF9800';
  return '#F44336';
}

function formatDate(d: Date): string {
  return d.toISOString().substring(0, 10);
}

function diffInDays(from: string, to: string): number {
  return Math.round(
    (new Date(to).getTime() - new Date(from).getTime()) / (1000 * 60 * 60 * 24),
  );
}
