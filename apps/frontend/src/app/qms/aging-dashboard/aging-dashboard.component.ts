import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { CapaAgingResponse, QmsService } from '../qms.service';
import { BarChartComponent } from '../../shared/charts/bar-chart/bar-chart.component';

@Component({
  selector: 'app-aging-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BarChartComponent],
  templateUrl: './aging-dashboard.component.html',
  styleUrl: './aging-dashboard.component.scss',
})
export class AgingDashboardComponent implements OnInit {
  private readonly qmsService = inject(QmsService);

  aging = signal<CapaAgingResponse | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  exportLoading = signal(false);

  readonly chartLabels = computed(() => {
    const a = this.aging();
    if (!a) return [];
    return [
      a.bucket0to7.label,
      a.bucket8to15.label,
      a.bucket16to30.label,
      a.bucketOver30.label,
    ];
  });

  readonly chartValues = computed(() => {
    const a = this.aging();
    if (!a) return [];
    return [
      a.bucket0to7.count,
      a.bucket8to15.count,
      a.bucket16to30.count,
      a.bucketOver30.count,
    ];
  });

  readonly chartColors = ['#3FA66A', '#56A4BB', '#E8A93C', '#D24A4A'];

  readonly avgDaysLabel = computed(() => {
    const a = this.aging();
    if (!a || a.avgResolutionDaysOpen == null) return '—';
    return `${Math.round(a.avgResolutionDaysOpen)} dias`;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.qmsService.getCapaAging().subscribe({
      next: (data) => {
        this.aging.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Erro ao carregar dados de aging.');
        this.loading.set(false);
      },
    });
  }

  exportCsv(): void {
    if (this.exportLoading()) return;
    this.exportLoading.set(true);
    this.qmsService.exportCapaAgingCsv().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `capa-aging-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        this.exportLoading.set(false);
      },
      error: () => {
        this.exportLoading.set(false);
      },
    });
  }
}
