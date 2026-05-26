import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface NcParetoResponse {
  byType: Record<string, number>;
  bySeverity: Record<string, number>;
}

@Component({
  selector: 'app-nc-pareto-widget',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) {
      <div class="widget-error" aria-label="Erro ao carregar widget">
        <span class="widget-error__icon">⚠</span>
        <span>Falha ao carregar</span>
      </div>
    } @else if (loading()) {
      <div class="widget-skeleton"></div>
    } @else {
      <span class="kpi-label">Pareto de NCs (30 dias)</span>
      <span class="kpi-value">{{ topType() }}</span>
      <span class="kpi-sub">tipo com mais NCs</span>
    }
  `,
})
export class NcParetoWidgetComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly topType = signal('N/A');

  ngOnInit(): void {
    this.http.get<NcParetoResponse>('/api/v1/analytics/nc/pareto?days=30').subscribe({
      next: (data) => {
        const entries = Object.entries(data.byType);
        const top = entries.sort(([, a], [, b]) => b - a)[0];
        this.topType.set(top ? `${top[0]}: ${top[1]}` : 'N/A');
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
