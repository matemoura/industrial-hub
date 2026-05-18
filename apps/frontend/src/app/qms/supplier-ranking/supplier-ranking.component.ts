import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { QmsService, SupplierQualityScore } from '../qms.service';

@Component({
  selector: 'app-supplier-ranking',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './supplier-ranking.component.html',
  styleUrl: './supplier-ranking.component.scss',
})
export class SupplierRankingComponent implements OnInit {
  private readonly qmsService = inject(QmsService);

  ranking = signal<SupplierQualityScore[]>([]);
  loading = signal(true);
  errorMsg = signal<string | null>(null);
  selectedDays = signal(90);

  readonly dayOptions = [30, 90, 180];

  ngOnInit(): void {
    this.load(this.selectedDays());
  }

  load(days: number): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.qmsService.getSupplierQualityRanking(days).subscribe({
      next: (list) => {
        this.ranking.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar ranking. Tente novamente.');
        this.loading.set(false);
      },
    });
  }

  changePeriod(days: number): void {
    this.selectedDays.set(days);
    this.load(days);
  }

  scoreColor(score: number): 'green' | 'amber' | 'red' {
    if (score >= 80) return 'green';
    if (score >= 60) return 'amber';
    return 'red';
  }

  formatScore(value: number): string {
    return value.toFixed(1);
  }
}
