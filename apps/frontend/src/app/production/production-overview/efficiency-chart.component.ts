import {
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import { LineChartModule } from '@swimlane/ngx-charts';

export interface ChartSeries {
  name: string;
  series: Array<{ name: string; value: number }>;
}

/**
 * US-108 / ADR-046 Decisão 2 — wrapper standalone para NgxCharts LineChart.
 * Isolado para facilitar override nos testes (NgxCharts usa SVG/D3 incompatível com JSDOM).
 */
@Component({
  selector: 'app-efficiency-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LineChartModule],
  template: `
    <ngx-charts-line-chart
      [results]="data()"
      [xAxis]="true"
      [yAxis]="true"
      [yScaleMin]="0"
      [yScaleMax]="100"
      [animations]="false"
      [referenceLines]="refLines"
      [showRefLines]="true"
      [autoScale]="false"
      [scheme]="'cool'">
    </ngx-charts-line-chart>
  `,
  styles: [`
    :host {
      display: block;
      height: 320px;
      width: 100%;
    }
  `],
})
export class EfficiencyChartComponent {
  readonly data = input.required<ChartSeries[]>();
  readonly refLines = [{ value: 80, name: 'Meta 80%' }];
}
