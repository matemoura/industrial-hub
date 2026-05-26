import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { KpiService } from '../../kpi.service';

@Component({
  selector: 'app-mttr-widget',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    @if (error()) {
      <div class="widget-error" aria-label="Erro ao carregar widget">
        <span class="widget-error__icon">⚠</span>
        <span>Falha ao carregar</span>
      </div>
    } @else if (loading()) {
      <div class="widget-skeleton"></div>
    } @else {
      <span class="kpi-label">MTTR Global</span>
      <span class="kpi-value">{{ formatted() }}</span>
      <span class="kpi-sub">tempo médio de reparo (corretivas)</span>
    }
  `,
})
export class MttrWidgetComponent implements OnInit {
  private readonly kpiService = inject(KpiService);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly formatted = signal('N/A');

  ngOnInit(): void {
    this.kpiService.getSummary().subscribe({
      next: (data) => {
        const v = data.mttrGlobalHours;
        this.formatted.set(v !== null ? v.toFixed(1) + ' h' : 'N/A');
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
