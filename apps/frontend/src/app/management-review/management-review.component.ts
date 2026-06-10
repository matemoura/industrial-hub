import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ManagementReviewService, ManagementReviewData } from '../common/management-review.service';
import { AuthService } from '../auth/auth.service';
import { SemaphoreChipComponent } from '../shared/semaphore-chip/semaphore-chip.component';

@Component({
  selector: 'app-management-review',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DecimalPipe, SemaphoreChipComponent],
  templateUrl: './management-review.component.html',
  styleUrl: './management-review.component.scss',
})
export class ManagementReviewComponent implements OnInit {
  private readonly service = inject(ManagementReviewService);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  data = signal<ManagementReviewData | null>(null);
  isLoading = signal(false);
  isExporting = signal(false);
  error = signal<string | null>(null);
  fromDate = signal<string>('');
  toDate = signal<string>('');

  readonly ncStatus = computed((): 'green' | 'amber' | 'red' => {
    const n = this.data()?.ncSummary.criticalOpen ?? 0;
    if (n === 0) return 'green';
    if (n <= 3) return 'amber';
    return 'red';
  });

  readonly capaStatus = computed((): 'green' | 'amber' | 'red' => {
    const n = this.data()?.capaSummary.overdueCount ?? 0;
    if (n === 0) return 'green';
    if (n <= 5) return 'amber';
    return 'red';
  });

  readonly calibStatus = computed((): 'green' | 'amber' | 'red' => {
    const n = this.data()?.calibrationSummary.overdueSchedules ?? 0;
    return n === 0 ? 'green' : 'red';
  });

  readonly riskStatus = computed((): 'green' | 'amber' | 'red' => {
    const n = this.data()?.riskSummary.criticalOpen ?? 0;
    return n === 0 ? 'green' : 'red';
  });

  readonly oeeStatus = computed((): 'green' | 'amber' | 'red' => {
    const oee = this.data()?.kpiSummary.oee30Days;
    if (oee == null) return 'amber';
    if (oee >= 65) return 'green';
    if (oee >= 50) return 'amber';
    return 'red';
  });

  ngOnInit(): void {
    const now = new Date();
    this.toDate.set(now.toISOString().slice(0, 10));
    const from = new Date(now);
    from.setDate(from.getDate() - 365);
    this.fromDate.set(from.toISOString().slice(0, 10));
  }

  loadIndicators(): void {
    if (!this.fromDate() || !this.toDate()) return;
    this.isLoading.set(true);
    this.error.set(null);
    this.service.getIndicators(this.fromDate(), this.toDate()).subscribe({
      next: (d) => {
        this.data.set(d);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Erro ao carregar indicadores. Verifique o período informado.');
        this.isLoading.set(false);
      },
    });
  }

  exportPdf(): void {
    if (!this.data()) return;
    this.isExporting.set(true);
    this.service.exportPdf(this.fromDate(), this.toDate()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `management-review-${this.fromDate()}-to-${this.toDate()}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.isExporting.set(false);
      },
      error: () => this.isExporting.set(false),
    });
  }
}
