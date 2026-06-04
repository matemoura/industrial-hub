import {
  ChangeDetectionStrategy,
  Component,
  signal,
  computed,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { QmsService, ReportFormat, ReportSection } from '../qms.service';

interface SectionToggle {
  id: ReportSection;
  label: string;
  checked: boolean;
}

@Component({
  selector: 'app-quality-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule],
  templateUrl: './quality-report.component.html',
  styleUrl: './quality-report.component.scss',
})
export class QualityReportComponent {
  private readonly qmsService = inject(QmsService);

  from = signal('');
  to = signal('');
  format = signal<ReportFormat>('PDF');

  sections = signal<SectionToggle[]>([
    { id: 'NCS', label: 'Não-Conformidades', checked: true },
    { id: 'CAPAS', label: 'CAPAs', checked: true },
    { id: 'GED', label: 'Documentos GED', checked: true },
    { id: 'RCA', label: 'Análise de Causa Raiz', checked: false },
  ]);

  generating = signal(false);
  error = signal<string | null>(null);
  successMsg = signal<string | null>(null);

  readonly validationError = computed(() => {
    const from = this.from();
    const to = this.to();
    const selected = this.sections().filter(s => s.checked);

    if (!from || !to) return 'Período obrigatório.';

    const fromDate = new Date(from);
    const toDate = new Date(to);
    if (toDate < fromDate) return 'Data final deve ser após a data inicial.';

    const diffDays = (toDate.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24);
    if (diffDays > 366) return 'Período máximo de 366 dias.';

    if (selected.length === 0) return 'Selecione ao menos uma seção.';

    return null;
  });

  readonly selectedSections = computed(() =>
    this.sections()
      .filter(s => s.checked)
      .map(s => s.id)
  );

  get isFormValid(): boolean {
    return !this.validationError();
  }

  toggleSection(id: ReportSection): void {
    this.sections.update(list =>
      list.map(s => s.id === id ? { ...s, checked: !s.checked } : s)
    );
  }

  generate(): void {
    if (!this.isFormValid || this.generating()) return;

    this.generating.set(true);
    this.error.set(null);
    this.successMsg.set(null);

    const fmt = this.format();

    this.qmsService.generateQualityReport({
      from: this.from(),
      to: this.to(),
      format: fmt,
      sections: this.selectedSections(),
    }).subscribe({
      next: (blob) => {
        const ext = fmt === 'PDF' ? 'pdf' : 'xlsx';
        const filename = `relatorio-qualidade-${this.from()}-${this.to()}.${ext}`;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        this.generating.set(false);
        this.successMsg.set(`Relatório gerado com sucesso: ${filename}`);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Erro ao gerar relatório. Tente novamente.');
        this.generating.set(false);
      },
    });
  }
}
