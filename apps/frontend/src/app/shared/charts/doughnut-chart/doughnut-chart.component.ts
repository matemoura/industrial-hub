import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  input,
} from '@angular/core';
import { Chart, DoughnutController, ArcElement, Tooltip, Legend } from 'chart.js';

Chart.register(DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-doughnut-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas></canvas>`,
  styles: [
    `
      :host {
        display: block;
        position: relative;
        width: 100%;
        max-width: 400px;
        margin: 0 auto;
      }
      canvas {
        width: 100% !important;
      }
    `,
  ],
})
export class DoughnutChartComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  labels = input<string[]>([]);
  values = input<number[]>([]);
  colors = input<string[]>([]);

  private chart: Chart | null = null;

  ngAfterViewInit(): void {
    this.buildChart();
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private buildChart(): void {
    const ctx = this.canvasRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const defaultColors = [
      '#0099B8',
      '#006B82',
      '#00C4E8',
      '#38A169',
      '#E53E3E',
      '#DD6B20',
      '#805AD5',
      '#D53F8C',
    ];

    const bgColors =
      this.colors().length > 0
        ? this.colors()
        : this.labels().map((_, i) => defaultColors[i % defaultColors.length]);

    this.chart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: this.labels(),
        datasets: [
          {
            data: this.values(),
            backgroundColor: bgColors,
            borderColor: '#ffffff',
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        plugins: {
          legend: { display: true, position: 'right' },
          tooltip: { mode: 'nearest' },
        },
      },
    });
  }
}
