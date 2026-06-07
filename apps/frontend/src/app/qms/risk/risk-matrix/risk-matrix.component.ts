import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  RiskService,
  RiskMatrixResponse,
  RiskLevel,
  rpnToRiskLevel,
} from '../../risk.service';

@Component({
  selector: 'app-risk-matrix',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './risk-matrix.component.html',
  styleUrl: './risk-matrix.component.scss',
})
export class RiskMatrixComponent implements OnInit {
  private readonly riskService = inject(RiskService);

  matrix = signal<RiskMatrixResponse | null>(null);
  loading = signal(false);

  // Severity Y (top = high = critical)
  readonly rows = [10, 9, 8, 7, 6, 5, 4, 3, 2, 1];
  // Occurrence X (left = 1, right = 10)
  readonly cols = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  readonly riskLevelColors: Record<RiskLevel, string> = {
    CRITICAL: '#D24A4A',
    HIGH: '#F97316',
    MEDIUM: '#E8A93C',
    LOW: '#3FA66A',
  };

  readonly riskLevelLabels: Record<RiskLevel, string> = {
    CRITICAL: 'Crítico (RPN > 200)',
    HIGH: 'Alto (RPN 101–200)',
    MEDIUM: 'Médio (RPN 31–100)',
    LOW: 'Baixo (RPN ≤ 30)',
  };

  ngOnInit(): void {
    this.loading.set(true);
    this.riskService.getRiskMatrix().subscribe({
      next: (m) => {
        this.matrix.set(m);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  getCellCount(severity: number, occurrence: number): number {
    return (
      this.matrix()?.cells.find(
        (c) => c.severity === severity && c.occurrence === occurrence
      )?.count ?? 0
    );
  }

  getCellLevel(severity: number, occurrence: number): RiskLevel {
    return rpnToRiskLevel(severity * occurrence);
  }

  getCellColor(severity: number, occurrence: number): string {
    return this.riskLevelColors[this.getCellLevel(severity, occurrence)];
  }
}
