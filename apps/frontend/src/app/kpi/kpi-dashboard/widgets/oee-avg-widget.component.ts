import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { KpiService } from '../../kpi.service';

@Component({
  selector: 'app-oee-avg-widget',
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
      <span class="kpi-label">OEE Médio (30 dias)</span>
      <span class="kpi-value" [class.value--danger]="isBelow()">{{ formatted() }}</span>
      @if (isBelow()) {
        <span class="kpi-alert">Abaixo do alvo (65%)</span>
      }
    }
  `,
})
export class OeeAvgWidgetComponent implements OnInit {
  private readonly kpiService = inject(KpiService);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly formatted = signal('N/A');
  readonly isBelow = signal(false);

  ngOnInit(): void {
    this.kpiService.getSummary().subscribe({
      next: (data) => {
        const v = data.oeeAvgLast30Days;
        this.formatted.set(v !== null ? (v * 100).toFixed(1) + '%' : 'N/A');
        this.isBelow.set(v !== null && v < 0.65);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
