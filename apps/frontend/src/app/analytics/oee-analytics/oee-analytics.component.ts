import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { AnalyticsService, OeeTrendResponse } from '../analytics.service';
import { LineChartComponent } from '../../shared/charts/line-chart/line-chart.component';
import { AdminService, Shift } from '../../admin/admin.service';

@Component({
  selector: 'app-oee-analytics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LineChartComponent],
  templateUrl: './oee-analytics.component.html',
  styleUrl: './oee-analytics.component.scss',
})
export class OeeAnalyticsComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);
  private readonly adminService = inject(AdminService);

  readonly periodOptions = [4, 8, 12, 26, 52];
  selectedWeeks = signal(12);
  excludeDowntime = signal(false);
  loading = signal(false);
  errorMsg = signal<string | null>(null);
  data = signal<OeeTrendResponse | null>(null);

  // Shift filter
  shifts = signal<Shift[]>([]);
  selectedShiftId = signal<string | null>(null);

  readonly chartLabels = computed(() => this.data()?.weekLabels ?? []);
  readonly chartValues = computed(
    () => (this.data()?.oeeValues ?? []).map((v) => (v !== null ? +(v * 100).toFixed(1) : null)),
  );
  readonly hasData = computed(() => (this.data()?.weekLabels ?? []).length > 0);

  readonly tableRows = computed(() => {
    const d = this.data();
    if (!d) return [];
    return d.weekLabels.map((label, i) => ({
      label,
      oee: d.oeeValues[i] !== null ? +(d.oeeValues[i]! * 100).toFixed(1) : null,
      samples: d.sampleCounts[i] ?? 0,
    }));
  });

  ngOnInit(): void {
    this.loadShifts();
    this.load();
  }

  loadShifts(): void {
    this.adminService.getShifts().subscribe({
      next: (list) => this.shifts.set(list),
      error: (err) => console.error('Erro ao carregar turnos', err),
    });
  }

  onShiftChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedShiftId.set(value || null);
    this.load();
  }

  onPeriodChange(event: Event): void {
    const weeks = Number((event.target as HTMLSelectElement).value);
    this.selectedWeeks.set(weeks);
    this.load();
  }

  onToggleExcludeDowntime(): void {
    this.excludeDowntime.set(!this.excludeDowntime());
    this.load();
  }

  print(): void {
    window.print();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.analyticsService.getOeeTrend(this.selectedWeeks(), this.excludeDowntime(), this.selectedShiftId() ?? undefined).subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar dados de OEE.');
        this.loading.set(false);
      },
    });
  }
}
