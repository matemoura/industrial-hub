import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { TrainingRecord, TrainingService } from '../training.service';

@Component({
  selector: 'app-my-training-records',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SlicePipe],
  templateUrl: './my-training-records.component.html',
  styleUrl: './my-training-records.component.scss',
})
export class MyTrainingRecordsComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);

  records = signal<TrainingRecord[]>([]);
  loading = signal(false);

  readonly valid    = computed(() => this.records().filter((r) => r.passed && this.statusOf(r) === 'valid'));
  readonly expiring = computed(() => this.records().filter((r) => r.passed && this.statusOf(r) === 'expiring'));
  readonly expired  = computed(() => this.records().filter((r) => r.passed && this.statusOf(r) === 'expired'));
  readonly failed   = computed(() => this.records().filter((r) => !r.passed));

  ngOnInit(): void {
    this.loading.set(true);
    this.trainingService.getMyRecords().subscribe({
      next: (list) => { this.records.set(list); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  statusOf(r: TrainingRecord): 'valid' | 'expiring' | 'expired' | 'no-expiry' {
    if (!r.expiresAt) return 'no-expiry';
    const d = new Date(r.expiresAt);
    const now = new Date();
    const in30 = new Date(); in30.setDate(in30.getDate() + 30);
    if (d < now) return 'expired';
    if (d <= in30) return 'expiring';
    return 'valid';
  }

  downloadCertificate(r: TrainingRecord): void {
    this.trainingService.getCertificateUrl(r.id).subscribe({
      next: (res) => window.open(res.url, '_blank', 'noopener,noreferrer'),
    });
  }
}
