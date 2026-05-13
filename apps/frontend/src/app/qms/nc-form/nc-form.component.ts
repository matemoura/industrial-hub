import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NcType, NcSeverity, QmsService } from '../qms.service';

@Component({
  selector: 'app-nc-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './nc-form.component.html',
  styleUrl: './nc-form.component.scss',
})
export class NcFormComponent {
  private readonly qmsService = inject(QmsService);
  private readonly router = inject(Router);

  title = signal('');
  type = signal<NcType | ''>('');
  severity = signal<NcSeverity | ''>('');
  description = signal('');
  loading = signal(false);
  errorMsg = signal<string | null>(null);

  readonly ncTypes: NcType[] = ['PROCESS', 'PRODUCT', 'SUPPLIER', 'EQUIPMENT', 'OTHER'];
  readonly ncSeverities: NcSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  readonly typeLabels: Record<NcType, string> = {
    PROCESS: 'Processo',
    PRODUCT: 'Produto',
    SUPPLIER: 'Fornecedor',
    EQUIPMENT: 'Equipamento',
    OTHER: 'Outro',
  };

  readonly severityLabels: Record<NcSeverity, string> = {
    LOW: 'Baixa',
    MEDIUM: 'Média',
    HIGH: 'Alta',
    CRITICAL: 'Crítica',
  };

  get isValid(): boolean {
    return this.title().trim().length > 0 && this.type() !== '' && this.severity() !== '';
  }

  submit(): void {
    if (!this.isValid || this.loading()) return;

    this.loading.set(true);
    this.errorMsg.set(null);

    this.qmsService
      .createNc({
        title: this.title().trim(),
        type: this.type() as NcType,
        severity: this.severity() as NcSeverity,
        description: this.description().trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/qms/non-conformances'], {
            state: { toast: 'Não-conformidade registrada com sucesso' },
          });
        },
        error: (err) => {
          this.errorMsg.set(err?.error?.message ?? 'Erro ao registrar NC. Tente novamente.');
          this.loading.set(false);
        },
      });
  }
}
