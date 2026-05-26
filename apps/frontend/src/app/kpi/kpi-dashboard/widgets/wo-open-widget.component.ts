import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { KpiService } from '../../kpi.service';

@Component({
  selector: 'app-wo-open-widget',
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
      <span class="kpi-label">OSs Abertas</span>
      <span class="kpi-value">{{ count() }}</span>
      <span class="kpi-sub">ordens em aberto ou andamento</span>
    }
  `,
})
export class WoOpenWidgetComponent implements OnInit {
  private readonly kpiService = inject(KpiService);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly count = signal(0);

  ngOnInit(): void {
    this.kpiService.getSummary().subscribe({
      next: (data) => {
        this.count.set(data.totalWorkOrdersOpen);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
