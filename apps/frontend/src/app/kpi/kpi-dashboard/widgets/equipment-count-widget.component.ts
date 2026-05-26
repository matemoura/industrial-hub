import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { KpiService } from '../../kpi.service';

@Component({
  selector: 'app-equipment-count-widget',
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
      <span class="kpi-label">Equipamentos Ativos</span>
      <span class="kpi-value">{{ count() }}</span>
      <span class="kpi-sub">equipamentos em operação</span>
    }
  `,
})
export class EquipmentCountWidgetComponent implements OnInit {
  private readonly kpiService = inject(KpiService);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly count = signal(0);

  ngOnInit(): void {
    this.kpiService.getSummary().subscribe({
      next: (data) => {
        this.count.set(data.activeEquipmentCount);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
