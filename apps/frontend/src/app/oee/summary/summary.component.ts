import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GroupBy, OeeService, PeriodSummaryDto } from '../oee.service';

@Component({
  selector: 'app-summary',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './summary.component.html',
  styleUrl: './summary.component.scss',
  // ChangeDetectionStrategy.OnPush não aplicado aqui porque o componente usa
  // ngModel bidirecional que exige default CD para o input[type=file]
})
export class SummaryComponent {
  private readonly oeeService = inject(OeeService);

  // ─── Period summary ───────────────────────────────────────────────────────
  startDate = signal('');
  endDate   = signal('');
  groupBy   = signal<GroupBy>('DAY');
  rows      = signal<PeriodSummaryDto[]>([]);
  loading   = signal(false);
  error     = signal<string | null>(null);

  readonly groupByOptions: GroupBy[] = ['DAY', 'WEEK', 'MONTH'];

  // ─── Period summary ───────────────────────────────────────────────────────
  formatAvailability(value: number | null): string {
    if (value == null) return '—';
    return (value * 100).toFixed(2) + '%';
  }

  exportCsv(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.oeeService.exportSummary(this.startDate(), this.endDate(), this.groupBy()).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href     = url;
      a.download = `oee-summary-${this.startDate()}-${this.endDate()}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  search(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.loading.set(true);
    this.error.set(null);
    this.oeeService.getSummary(this.startDate(), this.endDate(), this.groupBy()).subscribe({
      next:  (data) => { this.rows.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Failed to load data. Check the date range and try again.'); this.loading.set(false); },
    });
  }
}
