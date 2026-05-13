import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NcResponse, NcStatus, QmsService } from '../qms.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-nc-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
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

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadNc(id);
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

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return iso.replace('T', ' ').slice(0, 16);
  }
}
