import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IndirectActivityDto, OeeService } from '../oee.service';

@Component({
  selector: 'app-indirect-activities',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './indirect-activities.component.html',
  styleUrl: './indirect-activities.component.scss',
})
export class IndirectActivitiesComponent {
  private readonly oeeService = inject(OeeService);

  startDate = signal('');
  endDate = signal('');
  workerIdInput = signal('');
  rows = signal<IndirectActivityDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  formatPercent(value: number): string {
    return (value * 100).toFixed(2) + '%';
  }

  search(): void {
    if (!this.startDate() || !this.endDate()) return;
    this.loading.set(true);
    this.error.set(null);
    const wid = this.workerIdInput() ? Number(this.workerIdInput()) : undefined;
    this.oeeService.getIndirectActivities(this.startDate(), this.endDate(), wid).subscribe({
      next: (data) => { this.rows.set(data); this.loading.set(false); },
      error: () => { this.error.set('Failed to load data. Check the date range and try again.'); this.loading.set(false); },
    });
  }
}
