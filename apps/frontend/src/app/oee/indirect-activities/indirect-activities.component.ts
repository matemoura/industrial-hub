import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IndirectActivityDto, OeeService, WorkerDto } from '../oee.service';

@Component({
  selector: 'app-indirect-activities',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './indirect-activities.component.html',
  styleUrl: './indirect-activities.component.scss',
})
export class IndirectActivitiesComponent implements OnInit {
  private readonly oeeService = inject(OeeService);

  startDate = signal('');
  endDate = signal('');
  rows = signal<IndirectActivityDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  /** Workers loaded from API — used by the search form dropdown (AC4) */
  allWorkers = signal<WorkerDto[]>([]);
  /** Worker selected in the search form — sent to the API as filter (AC4, AC5) */
  searchWorkerId = signal<number | null>(null);

  ngOnInit(): void {
    this.oeeService.getWorkers().subscribe((workers) => this.allWorkers.set(workers));
  }

  formatPercent(value: number): string {
    return (value * 100).toFixed(2) + '%';
  }

  search(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.loading.set(true);
    this.error.set(null);
    const wid = this.searchWorkerId() ?? undefined;
    this.oeeService.getIndirectActivities(this.startDate(), this.endDate(), wid).subscribe({
      next: (data) => { this.rows.set(data); this.loading.set(false); },
      error: () => { this.error.set('Failed to load data. Check the date range and try again.'); this.loading.set(false); },
    });
  }
}
