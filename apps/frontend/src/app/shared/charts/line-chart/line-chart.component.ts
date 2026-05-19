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
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
  Filler,
  type ChartDataset,
} from 'chart.js';

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
  Filler,
);

@Component({
  selector: 'app-line-chart',
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
export class LineChartComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  labels = input<string[]>([]);
  values = input<(number | null)[]>([]);
  referenceValue = input<number | undefined>(undefined);
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

    const datasets: ChartDataset<'line'>[] = [
      {
        label: this.yLabel(),
        data: this.values() as (number | null)[],
        borderColor: '#0099B8',
        backgroundColor: 'rgba(0,153,184,0.1)',
        borderWidth: 2,
        pointRadius: 4,
        pointBackgroundColor: '#0099B8',
        tension: 0.3,
        fill: true,
        spanGaps: false,
      },
    ];

    const ref = this.referenceValue();
    if (ref !== undefined) {
      datasets.push({
        label: `Referência (${ref}%)`,
        data: this.labels().map(() => ref),
        borderColor: '#e53e3e',
        borderWidth: 2,
        borderDash: [6, 4],
        pointRadius: 0,
        fill: false,
      } as ChartDataset<'line'>);
    }

    this.chart = new Chart(ctx, {
      type: 'line',
      data: { labels: this.labels(), datasets },
      options: {
        responsive: true,
        plugins: {
          legend: { display: true, position: 'top' },
          tooltip: { mode: 'index', intersect: false },
        },
        scales: {
          y: {
            title: { display: !!this.yLabel(), text: this.yLabel() },
            beginAtZero: true,
          },
          x: { ticks: { maxRotation: 45 } },
        },
      },
    });
  }
}
