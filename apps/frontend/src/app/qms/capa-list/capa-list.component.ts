import { SlicePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActionStatus, ActionType, CAPASummary, Page, QmsService } from '../qms.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-capa-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, SlicePipe],
  templateUrl: './capa-list.component.html',
  styleUrl: './capa-list.component.scss',
})
export class CapaListComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly authService = inject(AuthService);
  readonly role = this.authService.role;

  page = signal<Page<CAPASummary> | null>(null);
  loading = signal(true);

  filterType = signal<ActionType | ''>('');
  filterStatus = signal<ActionStatus | ''>('');

  readonly types: ActionType[] = ['CORRECTIVE', 'PREVENTIVE'];
  readonly statuses: ActionStatus[] = ['PENDING', 'PENDING_EFFECTIVENESS', 'DONE'];

  readonly typeLabels: Record<ActionType, string> = {
    CORRECTIVE: 'Corretiva',
    PREVENTIVE: 'Preventiva',
  };

  readonly statusLabels: Record<ActionStatus, string> = {
    PENDING: 'Pendente',
    PENDING_EFFECTIVENESS: 'Aguard. Eficácia',
    DONE: 'Concluída',
  };

  ngOnInit(): void {
    this.loadList(0);
  }

  loadList(pageIndex: number): void {
    this.loading.set(true);
    this.qmsService.listCapas({
      type: this.filterType() || undefined,
      status: this.filterStatus() || undefined,
      page: pageIndex,
    }).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  applyFilters(): void {
    this.loadList(0);
  }

  goToPage(index: number): void {
    this.loadList(index);
  }
}
