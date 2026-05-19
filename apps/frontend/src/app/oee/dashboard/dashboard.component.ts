import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OeeService, WorkerDto, WorkerOeeDto } from '../oee.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly oeeService = inject(OeeService);

  startDate = signal('');
  endDate = signal('');
  rows = signal<WorkerOeeDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  /** Workers loaded from API — used by the search form dropdown (AC4) */
  allWorkers = signal<WorkerDto[]>([]);
  /** Worker selected in the search form — sent to the API as filter (AC4, AC5) */
  searchWorkerId = signal<number | null>(null);
  /** Worker selected for local post-result filtering */
  selectedWorkerId = signal<number | null>(null);

  /** Toggle: exclude planned downtimes from OEE calculation */
  excludePlannedDowntime = signal(false);

  /** Unique workers derived from loaded rows — used by the local result filter */
  workers = computed(() =>
    [...new Map(this.rows().map((r) => [r.workerId, r.workerName])).entries()].map(
      ([id, name]) => ({ id, name }),
    ),
  );

  filteredRows = computed(() => {
    const wid = this.selectedWorkerId();
    return wid == null ? this.rows() : this.rows().filter((r) => r.workerId === wid);
  });

  ngOnInit(): void {
    this.oeeService.getWorkers().subscribe((workers) => this.allWorkers.set(workers));
  }

  formatAvailability(value: number | null): string {
    if (value == null) return '—';
    return (value * 100).toFixed(2) + '%';
  }

  exportCsv(): void {
    if (!this.startDate() || !this.endDate()) return;
    const wid = this.searchWorkerId() ?? undefined;
    this.oeeService.exportDashboard(this.startDate(), this.endDate(), wid).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `oee-dashboard-${this.startDate()}-${this.endDate()}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  search(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.loading.set(true);
    this.error.set(null);
    this.selectedWorkerId.set(null);
    const wid = this.searchWorkerId() ?? undefined;
    const excludePD = this.excludePlannedDowntime() || undefined;
    this.oeeService.getDashboard(this.startDate(), this.endDate(), wid, excludePD).subscribe({
      next: (data) => {
        this.rows.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load data. Check the date range and try again.');
        this.loading.set(false);
      },
    });
  }
}
