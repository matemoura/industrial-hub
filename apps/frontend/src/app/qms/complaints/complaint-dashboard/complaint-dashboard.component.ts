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
import {
  ComplaintService,
  ComplaintIndicators,
  ComplaintStatus,
  NcSeverity,
  ComplaintSource,
} from '../../complaints.service';

@Component({
  selector: 'app-complaint-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './complaint-dashboard.component.html',
  styleUrl: './complaint-dashboard.component.scss',
})
export class ComplaintDashboardComponent implements OnInit {
  private readonly complaintService = inject(ComplaintService);

  indicators = signal<ComplaintIndicators | null>(null);
  loading = signal(false);

  fromDate = '';
  toDate = '';

  readonly notReported = computed(() => {
    const ind = this.indicators();
    if (!ind) return 0;
    return Math.max(0, ind.totalReceived - ind.reportedToAnvisa);
  });

  readonly closedCount = computed(() => this.indicators()?.byStatus?.['CLOSED'] ?? 0);
  readonly underInvestigation = computed(() => this.indicators()?.byStatus?.['UNDER_INVESTIGATION'] ?? 0);

  readonly statusColors: Record<ComplaintStatus, string> = {
    RECEIVED: '#818286',
    UNDER_INVESTIGATION: '#56A4BB',
    INVESTIGATION_COMPLETED: '#E8A93C',
    CLOSED: '#3FA66A',
  };

  readonly statusLabels: Record<ComplaintStatus, string> = {
    RECEIVED: 'Recebida',
    UNDER_INVESTIGATION: 'Em Investigação',
    INVESTIGATION_COMPLETED: 'Inv. Concluída',
    CLOSED: 'Encerrada',
  };

  readonly severityColors: Record<NcSeverity, string> = {
    LOW: '#3FA66A',
    MEDIUM: '#E8A93C',
    HIGH: '#D24A4A',
    CRITICAL: '#9C0000',
  };

  readonly sourceLabels: Record<ComplaintSource, string> = {
    CLIENT: 'Cliente',
    DISTRIBUTOR: 'Distribuidor',
    REGULATORY_BODY: 'Órgão Regulatório',
    INTERNAL: 'Interna',
  };

  readonly statusEntries = computed((): [ComplaintStatus, number][] => {
    const ind = this.indicators();
    if (!ind) return [];
    return (Object.entries(ind.byStatus) as [ComplaintStatus, number][]);
  });

  readonly sourceEntries = computed((): [ComplaintSource, number][] => {
    const ind = this.indicators();
    if (!ind) return [];
    return (Object.entries(ind.bySource) as [ComplaintSource, number][]);
  });

  readonly severityEntries = computed((): [NcSeverity, number][] => {
    const ind = this.indicators();
    if (!ind) return [];
    return (Object.entries(ind.bySeverity) as [NcSeverity, number][]);
  });

  ngOnInit(): void {
    const now = new Date();
    this.toDate = now.toISOString().slice(0, 10);
    const fromDate = new Date(now);
    fromDate.setFullYear(fromDate.getFullYear() - 1);
    this.fromDate = fromDate.toISOString().slice(0, 10);
    this.loadIndicators();
  }

  loadIndicators(): void {
    if (!this.fromDate || !this.toDate) return;
    this.loading.set(true);
    this.complaintService.getIndicators(this.fromDate, this.toDate).subscribe({
      next: (ind) => {
        this.indicators.set(ind);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
