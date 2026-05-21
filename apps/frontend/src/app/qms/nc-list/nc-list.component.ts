import { SlicePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  NcKpiSummary,
  NcSeverity,
  NcStatus,
  NcSummaryItem,
  NcType,
  PageResponse,
  QmsService,
} from '../qms.service';
import { AuthService } from '../../auth/auth.service';
import { SlaBreachedChipComponent } from '../../shared/sla-breached-chip/sla-breached-chip.component';

@Component({
  selector: 'app-nc-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, SlicePipe, SlaBreachedChipComponent],
  templateUrl: './nc-list.component.html',
  styleUrl: './nc-list.component.scss',
})
export class NcListComponent implements OnInit {
  private readonly qmsService = inject(QmsService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = this.authService.role;

  kpi = signal<NcKpiSummary | null>(null);
  page = signal<PageResponse<NcSummaryItem> | null>(null);
  loading = signal(false);
  toast = signal<string | null>(null);

  filterStatus = signal<NcStatus | ''>('');
  filterSeverity = signal<NcSeverity | ''>('');
  filterType = signal<NcType | ''>('');
  filterSlaBreached = signal(false);

  readonly statuses: NcStatus[] = ['OPEN', 'IN_ANALYSIS', 'CLOSED'];
  readonly severities: NcSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly types: NcType[] = ['PROCESS', 'PRODUCT', 'SUPPLIER', 'EQUIPMENT', 'OTHER'];

  readonly statusLabels: Record<NcStatus, string> = {
    OPEN: 'Aberta',
    IN_ANALYSIS: 'Em análise',
    CLOSED: 'Fechada',
  };

  readonly severityLabels: Record<NcSeverity, string> = {
    LOW: 'Baixa',
    MEDIUM: 'Média',
    HIGH: 'Alta',
    CRITICAL: 'Crítica',
  };

  readonly typeLabels: Record<NcType, string> = {
    PROCESS: 'Processo',
    PRODUCT: 'Produto',
    SUPPLIER: 'Fornecedor',
    EQUIPMENT: 'Equipamento',
    OTHER: 'Outro',
  };

  get isSupervisor(): boolean {
    return this.role() === 'SUPERVISOR' || this.role() === 'ADMIN';
  }

  ngOnInit(): void {
    const toastMsg = (history.state as { toast?: string })?.toast;
    if (toastMsg) {
      this.toast.set(toastMsg);
      setTimeout(() => this.toast.set(null), 4000);
    }
    this.loadKpi();
    this.loadList(0);
  }

  loadKpi(): void {
    this.qmsService.getKpiSummary().subscribe((kpi) => this.kpi.set(kpi));
  }

  loadList(pageIndex: number): void {
    this.loading.set(true);
    const filters = {
      status: this.filterStatus() || undefined,
      severity: this.filterSeverity() || undefined,
      type: this.filterType() || undefined,
      slaBreached: this.filterSlaBreached() ? true : undefined,
    };
    this.qmsService.listNcs(filters, pageIndex).subscribe({
      next: (p) => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  applyFilters(): void {
    this.loadList(0);
  }

  clearFilters(): void {
    this.filterStatus.set('');
    this.filterSeverity.set('');
    this.filterType.set('');
    this.filterSlaBreached.set(false);
    this.loadList(0);
  }

  goToPage(index: number): void {
    this.loadList(index);
  }

  exportCsv(): void {
    this.qmsService.exportCsv().subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'ncs-export.csv';
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  openDetail(id: string): void {
    this.router.navigate(['/qms/non-conformances', id]);
  }
}
