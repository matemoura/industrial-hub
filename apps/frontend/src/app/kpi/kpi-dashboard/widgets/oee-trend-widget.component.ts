import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface OeeTrendResponse {
  weekLabels: string[];
  oeeValues: (number | null)[];
}

@Component({
  selector: 'app-oee-trend-widget',
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
      <span class="kpi-label">Tendência OEE (12 semanas)</span>
      <span class="kpi-value">{{ latestFormatted() }}</span>
      <span class="kpi-sub">última semana com dado</span>
    }
  `,
})
export class OeeTrendWidgetComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly latestFormatted = signal('N/A');

  ngOnInit(): void {
    this.http.get<OeeTrendResponse>('/api/v1/analytics/oee/trend?weeks=12').subscribe({
      next: (data) => {
        const latest = [...data.oeeValues].reverse().find((v) => v !== null);
        this.latestFormatted.set(latest !== undefined ? (latest * 100).toFixed(1) + '%' : 'N/A');
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
