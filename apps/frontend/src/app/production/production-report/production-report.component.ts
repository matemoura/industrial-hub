import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AuthService } from '../../auth/auth.service';
import { PlanningSummaryRow, ProductFamily, ProductionService } from '../production.service';

@Component({
  selector: 'app-production-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './production-report.component.html',
  styleUrl: './production-report.component.scss',
})
export class ProductionReportComponent implements OnInit {
  private readonly service = inject(ProductionService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly BASE = '/api/v1/production';

  readonly families = signal<ProductFamily[]>([]);
  readonly rows = signal<PlanningSummaryRow[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly hasResults = computed(() => this.rows().length > 0);

  // Filters
  readonly familyCode = signal<string>('');

  // Default: last 30 days
  private readonly today = new Date();
  readonly fromDate = signal<string>(this.formatDate(new Date(this.today.getTime() - 30 * 86400000)));
  readonly toDate = signal<string>(this.formatDate(this.today));

  readonly filtersValid = computed(() =>
    !!this.fromDate() && !!this.toDate() && this.fromDate() <= this.toDate()
  );

  ngOnInit(): void {
    this.service.listFamilies().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (f) => this.families.set(f),
      error: () => {},
    });
  }

  load(): void {
    if (!this.filtersValid()) return;
    this.loading.set(true);
    this.error.set(null);

    let p = new HttpParams()
      .set('from', this.fromDate())
      .set('to', this.toDate());
    if (this.familyCode()) p = p.set('familyCode', this.familyCode());

    this.http
      .get<PlanningSummaryRow[]>(`${this.BASE}/reports/planning-summary`, { params: p })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.rows.set(data);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erro ao gerar relatório. Tente novamente.');
          this.loading.set(false);
        },
      });
  }

  exportCsv(): void {
    if (!this.hasResults()) return;
    // URLSearchParams garante encoding correto de todos os parâmetros
    const p = new URLSearchParams({ from: this.fromDate(), to: this.toDate() });
    if (this.familyCode()) p.set('familyCode', this.familyCode());
    // noopener,noreferrer: consistente com padrão SEC-074 do projeto
    window.open(`${this.BASE}/reports/planning-summary/export?${p.toString()}`, '_blank', 'noopener,noreferrer');
  }

  formatEfficiency(value: number | null): string {
    return value !== null && value !== undefined ? `${value.toFixed(1)}%` : '—';
  }

  private formatDate(d: Date): string {
    return d.toISOString().split('T')[0];
  }
}
