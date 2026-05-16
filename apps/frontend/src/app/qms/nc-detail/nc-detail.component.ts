import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ActionResponse, NcResponse, NcStatus, QmsService } from '../qms.service';
import { AuthService } from '../../auth/auth.service';
import { NcRcaComponent } from './nc-rca/nc-rca.component';

@Component({
  selector: 'app-nc-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, NcRcaComponent],
  templateUrl: './nc-detail.component.html',
  styleUrl: './nc-detail.component.scss',
})
export class NcDetailComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly role = this.authService.role;

  nc = signal<NcResponse | null>(null);
  loading = signal(true);
  errorMsg = signal<string | null>(null);
  transitionLoading = signal(false);

  actions = signal<ActionResponse[]>([]);
  actionsLoading = signal(false);
  actionError = signal<string | null>(null);

  showActionForm = signal(false);
  actionDescription = signal('');
  actionResponsible = signal('');
  actionDueDate = signal('');
  actionSubmitting = signal(false);

  readonly statusLabels: Record<NcStatus, string> = {
    OPEN: 'Aberta',
    IN_ANALYSIS: 'Em análise',
    CLOSED: 'Fechada',
  };

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  get allowedTransitions(): { label: string; target: NcStatus }[] {
    const status = this.nc()?.status;
    if (!status || !this.isSupervisor) return [];
    if (status === 'OPEN') return [{ label: 'Iniciar Análise', target: 'IN_ANALYSIS' }];
    if (status === 'IN_ANALYSIS') return [
      { label: 'Fechar NC', target: 'CLOSED' },
      { label: 'Re-abrir', target: 'OPEN' },
    ];
    return [];
  }

  get isActionFormValid(): boolean {
    return this.actionDescription().trim().length > 0
      && this.actionResponsible().trim().length > 0
      && this.actionDueDate().length > 0;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadNc(id);
    this.loadActions(id);
  }

  loadNc(id: string): void {
    this.loading.set(true);
    this.qmsService.getNc(id).subscribe({
      next: (nc) => {
        this.nc.set(nc);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('NC não encontrada.');
        this.loading.set(false);
      },
    });
  }

  loadActions(id: string): void {
    this.actionsLoading.set(true);
    this.qmsService.listActions(id).subscribe({
      next: (list) => {
        this.actions.set(list);
        this.actionsLoading.set(false);
      },
      error: () => {
        this.actionsLoading.set(false);
      },
    });
  }

  transition(target: NcStatus): void {
    const nc = this.nc();
    if (!nc || this.transitionLoading()) return;

    const labelMap: Record<NcStatus, string> = {
      IN_ANALYSIS: 'iniciar análise',
      CLOSED: 'fechar',
      OPEN: 're-abrir',
    };
    if (!confirm(`Confirma ${labelMap[target]} esta NC?`)) return;

    this.transitionLoading.set(true);
    this.errorMsg.set(null);
    this.qmsService.transitionStatus(nc.id, target).subscribe({
      next: (updated) => {
        this.nc.set(updated);
        this.transitionLoading.set(false);
      },
      error: (err) => {
        this.errorMsg.set(err?.error?.message ?? 'Erro ao atualizar status. Tente novamente.');
        this.transitionLoading.set(false);
      },
    });
  }

  submitAction(): void {
    const nc = this.nc();
    if (!nc || !this.isActionFormValid || this.actionSubmitting()) return;

    this.actionSubmitting.set(true);
    this.actionError.set(null);

    this.qmsService.createAction(nc.id, {
      description: this.actionDescription().trim(),
      responsible: this.actionResponsible().trim(),
      dueDate: this.actionDueDate(),
    }).subscribe({
      next: (action) => {
        this.actions.update(list => [...list, action]);
        this.actionDescription.set('');
        this.actionResponsible.set('');
        this.actionDueDate.set('');
        this.showActionForm.set(false);
        this.actionSubmitting.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao criar ação. Tente novamente.');
        this.actionSubmitting.set(false);
      },
    });
  }

  completeAction(actionId: string): void {
    const nc = this.nc();
    if (!nc || !confirm('Confirma conclusão desta ação?')) return;

    this.qmsService.completeAction(nc.id, actionId).subscribe({
      next: (updated) => {
        this.actions.update(list => list.map(a => a.id === actionId ? updated : a));
        this.loadNc(nc.id);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao concluir ação.');
      },
    });
  }

  deleteAction(actionId: string): void {
    const nc = this.nc();
    if (!nc || !confirm('Excluir esta ação corretiva?')) return;

    this.qmsService.deleteAction(nc.id, actionId).subscribe({
      next: () => {
        this.actions.update(list => list.filter(a => a.id !== actionId));
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Erro ao excluir ação.');
      },
    });
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return iso.replace('T', ' ').slice(0, 16);
  }

  formatLocalDate(date: string | null): string {
    if (!date) return '—';
    return date;
  }
}
