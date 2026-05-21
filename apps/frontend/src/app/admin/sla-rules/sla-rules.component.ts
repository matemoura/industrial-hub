import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  SlaClassifierField,
  SlaEntityType,
  SlaRuleResponse,
  SlaService,
} from './sla.service';

type DialogMode = 'create' | null;

interface EditState {
  id: string;
  slaHours: number | null;
  escalateByEmail: boolean;
}

@Component({
  selector: 'app-sla-rules',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './sla-rules.component.html',
  styleUrl: './sla-rules.component.scss',
})
export class SlaRulesComponent implements OnInit {
  private readonly slaService = inject(SlaService);

  readonly rules = signal<SlaRuleResponse[]>([]);
  readonly loading = signal(true);
  readonly errorMsg = signal<string | null>(null);
  readonly successMsg = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly runningEscalation = signal(false);

  readonly dialogMode = signal<DialogMode>(null);

  // Form fields for create dialog
  readonly formEntityType = signal<SlaEntityType>('NC');
  readonly formClassifierField = signal<SlaClassifierField>('SEVERITY');
  readonly formClassifierValue = signal('');
  readonly formSlaHours = signal<number | null>(null);
  readonly formEscalateByEmail = signal(false);
  readonly formError = signal<string | null>(null);

  // Inline edit state
  readonly editState = signal<EditState | null>(null);

  readonly entityTypeLabels: Record<SlaEntityType, string> = {
    NC: 'Não-Conformidade',
    WORK_ORDER: 'Ordem de Serviço',
  };

  readonly classifierFieldLabels: Record<SlaClassifierField, string> = {
    SEVERITY: 'Severidade',
    PRIORITY: 'Prioridade',
  };

  readonly entityTypes: SlaEntityType[] = ['NC', 'WORK_ORDER'];
  readonly classifierFields: SlaClassifierField[] = ['SEVERITY', 'PRIORITY'];

  readonly isFormValid = computed(() => {
    const hours = this.formSlaHours();
    return (
      this.formClassifierValue().trim().length > 0 &&
      hours !== null &&
      hours >= 1 &&
      hours <= 8760
    );
  });

  readonly isEditValid = computed(() => {
    const state = this.editState();
    if (!state) return false;
    const h = state.slaHours;
    return h !== null && h >= 1 && h <= 8760;
  });

  ngOnInit(): void {
    this.loadRules();
  }

  loadRules(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.slaService.listSlaRules().subscribe({
      next: (list) => {
        this.rules.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Não foi possível carregar as regras de SLA.');
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    this.formEntityType.set('NC');
    this.formClassifierField.set('SEVERITY');
    this.formClassifierValue.set('');
    this.formSlaHours.set(null);
    this.formEscalateByEmail.set(false);
    this.formError.set(null);
    this.dialogMode.set('create');
  }

  closeDialog(): void {
    this.dialogMode.set(null);
    this.formError.set(null);
  }

  submitCreate(): void {
    const hours = this.formSlaHours();
    if (!this.isFormValid() || hours === null) {
      this.formError.set('Preencha todos os campos corretamente. SLA deve ser entre 1 e 8760 horas.');
      return;
    }
    this.submitting.set(true);
    this.slaService
      .createSlaRule({
        entityType: this.formEntityType(),
        classifierField: this.formClassifierField(),
        classifierValue: this.formClassifierValue().trim().toUpperCase(),
        slaHours: hours,
        escalateByEmail: this.formEscalateByEmail(),
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.closeDialog();
          this.showSuccess('Regra SLA criada.');
          this.loadRules();
        },
        error: (err) => {
          this.formError.set(err?.error?.message ?? 'Erro ao criar regra SLA.');
          this.submitting.set(false);
        },
      });
  }

  startEdit(rule: SlaRuleResponse): void {
    this.editState.set({
      id: rule.id,
      slaHours: rule.slaHours,
      escalateByEmail: rule.escalateByEmail,
    });
  }

  cancelEdit(): void {
    this.editState.set(null);
  }

  saveEdit(): void {
    const state = this.editState();
    if (!state || !this.isEditValid() || state.slaHours === null) return;
    this.submitting.set(true);
    this.slaService.updateSlaRule(state.id, { slaHours: state.slaHours, escalateByEmail: state.escalateByEmail }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.editState.set(null);
        this.showSuccess('Regra SLA atualizada.');
        this.loadRules();
      },
      error: (err) => {
        this.submitting.set(false);
        this.showError(err?.error?.message ?? 'Erro ao atualizar regra SLA.');
      },
    });
  }

  deactivate(rule: SlaRuleResponse): void {
    if (!confirm(`Desativar regra SLA para ${this.entityTypeLabels[rule.entityType]} / ${rule.classifierValue}?`)) return;
    this.slaService.deleteSlaRule(rule.id).subscribe({
      next: () => {
        this.rules.update((list) => list.filter((r) => r.id !== rule.id));
        this.showSuccess('Regra SLA desativada.');
      },
      error: (err) => {
        this.showError(err?.error?.message ?? 'Erro ao desativar regra SLA.');
      },
    });
  }

  runEscalation(): void {
    this.runningEscalation.set(true);
    this.slaService.runEscalationNow().subscribe({
      next: (result) => {
        this.runningEscalation.set(false);
        this.showSuccess(
          `Escalação concluída: ${result.breachedNcs} NCs e ${result.breachedWorkOrders} OSs marcadas.`,
        );
      },
      error: (err) => {
        this.runningEscalation.set(false);
        this.showError(err?.error?.message ?? 'Erro ao executar escalação.');
      },
    });
  }

  updateEditHours(value: string): void {
    const parsed = parseInt(value, 10);
    this.editState.update((s) => s ? { ...s, slaHours: isNaN(parsed) ? null : parsed } : s);
  }

  updateEditEmail(checked: boolean): void {
    this.editState.update((s) => s ? { ...s, escalateByEmail: checked } : s);
  }

  dismissError(): void {
    this.errorMsg.set(null);
  }

  private showSuccess(message: string): void {
    this.successMsg.set(message);
    this.errorMsg.set(null);
    setTimeout(() => this.successMsg.set(null), 4000);
  }

  private showError(message: string): void {
    this.errorMsg.set(message);
    this.successMsg.set(null);
  }
}
