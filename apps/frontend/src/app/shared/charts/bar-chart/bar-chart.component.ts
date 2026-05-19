import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  input,
} from '@angular/core';
import {
  Chart,
  BarController,
  BarElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
} from 'chart.js';

Chart.register(BarController, BarElement, LinearScale, CategoryScale, Tooltip, Legend);

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas></canvas>`,
  styles: [
    `
      :host {
        display: block;
        position: relative;
        width: 100%;
      }
      canvas {
        width: 100% !important;
      }
    `,
  ],
})
export class BarChartComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  labels = input<string[]>([]);
  values = input<number[]>([]);
  colors = input<string[]>([]);
  yLabel = input<string>('');

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
    ];

    const bgColors =
      this.colors().length > 0
        ? this.colors()
        : this.labels().map((_, i) => defaultColors[i % defaultColors.length]);

    this.chart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: this.labels(),
        datasets: [
          {
            label: this.yLabel(),
            data: this.values(),
            backgroundColor: bgColors,
            borderColor: bgColors.map((c) => c),
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        plugins: {
          legend: { display: false },
          tooltip: { mode: 'index', intersect: false },
        },
        scales: {
          y: {
            title: { display: !!this.yLabel(), text: this.yLabel() },
            beginAtZero: true,
            ticks: { precision: 0 },
          },
        },
      },
    });
  }
}
