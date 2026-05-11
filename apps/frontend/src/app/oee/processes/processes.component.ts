import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OeeService, ProcessEfficiencyDto, WorkerDto } from '../oee.service';

@Component({
  selector: 'app-processes',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './processes.component.html',
  styleUrl: './processes.component.scss',
})
export class ProcessesComponent implements OnInit {
  private readonly oeeService = inject(OeeService);

  startDate = signal('');
  endDate = signal('');
  rows = signal<ProcessEfficiencyDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  /** Workers loaded from API — used by the search form dropdown */
  allWorkers = signal<WorkerDto[]>([]);
  /** Worker selected in the search form — sent to the API as filter */
  searchWorkerId = signal<number | null>(null);

  ngOnInit(): void {
    this.oeeService.getWorkers().subscribe((workers) => this.allWorkers.set(workers));
  }

  formatHours(value: number): string {
    return value.toFixed(2) + 'h';
  }

  search(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.loading.set(true);
    this.error.set(null);
    const wid = this.searchWorkerId() ?? undefined;
    this.oeeService.getByProcess(this.startDate(), this.endDate(), wid).subscribe({
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
