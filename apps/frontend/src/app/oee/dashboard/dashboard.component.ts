import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OeeService, WorkerOeeDto } from '../oee.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly oeeService = inject(OeeService);

  startDate = signal('');
  endDate = signal('');
  workerIdInput = signal('');
  rows = signal<WorkerOeeDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  selectedWorkerId = signal<number | null>(null);

  workers = computed(() =>
    [...new Map(this.rows().map((r) => [r.workerId, r.workerName])).entries()].map(
      ([id, name]) => ({ id, name }),
    ),
  );

  filteredRows = computed(() => {
    const wid = this.selectedWorkerId();
    return wid == null ? this.rows() : this.rows().filter((r) => r.workerId === wid);
  });

  formatAvailability(value: number | null): string {
    if (value == null) return '—';
    return (value * 100).toFixed(2) + '%';
  }

  search(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.loading.set(true);
    this.error.set(null);
    this.selectedWorkerId.set(null);
    const wid = this.workerIdInput() ? Number(this.workerIdInput()) : undefined;
    this.oeeService.getDashboard(this.startDate(), this.endDate(), wid).subscribe({
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
