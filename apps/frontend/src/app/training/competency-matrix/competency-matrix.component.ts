import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CompetencyMatrixRow, CompetencyStatus, TrainingService } from '../training.service';

@Component({
  selector: 'app-competency-matrix',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, SlicePipe],
  templateUrl: './competency-matrix.component.html',
  styleUrl: './competency-matrix.component.scss',
})
export class CompetencyMatrixComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);

  rows = signal<CompetencyMatrixRow[]>([]);
  loading = signal(false);

  filterRole = signal('');
  filterUsername = signal('');

  readonly roles = computed(() => [...new Set(this.rows().map((r) => r.role))].sort());

  readonly filtered = computed(() =>
    this.rows().filter((r) => {
      const roleOk = !this.filterRole() || r.role === this.filterRole();
      const userOk = !this.filterUsername() || r.username.toLowerCase().includes(this.filterUsername().toLowerCase());
      return roleOk && userOk;
    }),
  );

  readonly statusColors: Record<string, string> = {
    VALID:    '#3FA66A',
    EXPIRING: '#E8A93C',
    EXPIRED:  '#D24A4A',
    MISSING:  '#818286',
  };

  readonly statusLabels: Record<string, string> = {
    VALID:    'Válido',
    EXPIRING: 'Vencendo',
    EXPIRED:  'Vencido',
    MISSING:  'Ausente',
  };

  ngOnInit(): void {
    this.loading.set(true);
    this.trainingService.getCompetencyMatrix().subscribe({
      next: (list) => { this.rows.set(list); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  exportCsv(): void {
    const header = 'Colaborador,Papel,Curso,Código,Status,Conclusão,Vencimento\n';
    const body = this.filtered().map((r) =>
      [r.username, r.role, `"${r.courseTitle}"`, r.courseCode, r.status,
       r.completedAt ?? '', r.expiresAt ?? ''].join(','),
    ).join('\n');

    const blob = new Blob([header + body], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'matriz-competencias.csv'; a.click();
    URL.revokeObjectURL(url);
  }
}
