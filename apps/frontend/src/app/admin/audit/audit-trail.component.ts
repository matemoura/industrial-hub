import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../auth/auth.service';
import {
  AuditFilters,
  AuditLogResponse,
  AuditRetentionConfig,
  AuditService,
} from './audit.service';

const AUDIT_MODULES = [
  'OEE',
  'QMS',
  'MAINTENANCE',
  'PRODUCTION',
  'TRAINING',
  'CHANGES',
  'MANAGEMENT_REVIEW',
  'AUTH',
] as const;

const COMMON_ACTIONS = [
  'USER_CREATED',
  'USER_UPDATED',
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'NC_CREATED',
  'NC_UPDATED',
  'NC_CLOSED',
  'CAPA_CREATED',
  'WORK_ORDER_CREATED',
  'DOCUMENT_UPLOADED',
  'CALIBRATION_RECORDED',
  'AUDIT_CREATED',
  'RISK_CREATED',
  'CHANGE_REQUEST_CREATED',
  'COMPLAINT_CREATED',
] as const;

@Component({
  selector: 'app-audit-trail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './audit-trail.component.html',
  styleUrl: './audit-trail.component.scss',
})
export class AuditTrailComponent implements OnInit {
  private readonly auditService = inject(AuditService);
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.role() === 'ADMIN');

  // ── Logs ────────────────────────────────────────────────────────────────────
  readonly logs = signal<AuditLogResponse[]>([]);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly currentPage = signal(0);
  readonly isLoading = signal(false);
  readonly expandedId = signal<string | null>(null);
  readonly errorMsg = signal<string | null>(null);

  // ── Filtros ─────────────────────────────────────────────────────────────────
  readonly filterUsername = signal('');
  readonly filterModule = signal('');
  readonly filterAction = signal('');
  readonly filterFrom = signal('');
  readonly filterTo = signal('');

  // ── Retenção ────────────────────────────────────────────────────────────────
  readonly retention = signal<AuditRetentionConfig | null>(null);
  readonly retentionDays = signal<number | null>(null);
  readonly isSavingRetention = signal(false);
  readonly retentionError = signal<string | null>(null);
  readonly retentionSuccess = signal<string | null>(null);

  readonly retentionValid = computed(() => {
    const v = this.retentionDays();
    return v != null && v >= 30 && v <= 3650;
  });

  readonly modules = AUDIT_MODULES;
  readonly commonActions = COMMON_ACTIONS;

  ngOnInit(): void {
    this.loadLogs();
    this.loadRetention();
  }

  loadLogs(page = 0): void {
    this.isLoading.set(true);
    this.errorMsg.set(null);
    this.currentPage.set(page);

    const filters: AuditFilters = {
      page,
      size: 20,
    };
    if (this.filterUsername()) filters.username = this.filterUsername();
    if (this.filterModule())   filters.module   = this.filterModule();
    if (this.filterAction())   filters.action   = this.filterAction();
    if (this.filterFrom())     filters.from     = this.filterFrom();
    if (this.filterTo())       filters.to       = this.filterTo();

    this.auditService.getAuditLogs(filters).subscribe({
      next: (page) => {
        this.logs.set(page.content);
        this.totalPages.set(page.totalPages);
        this.totalElements.set(page.totalElements);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMsg.set('Erro ao carregar logs de auditoria.');
        this.isLoading.set(false);
      },
    });
  }

  applyFilters(): void {
    this.loadLogs(0);
  }

  clearFilters(): void {
    this.filterUsername.set('');
    this.filterModule.set('');
    this.filterAction.set('');
    this.filterFrom.set('');
    this.filterTo.set('');
    this.loadLogs(0);
  }

  prevPage(): void {
    if (this.currentPage() > 0) {
      this.loadLogs(this.currentPage() - 1);
    }
  }

  nextPage(): void {
    if (this.currentPage() < this.totalPages() - 1) {
      this.loadLogs(this.currentPage() + 1);
    }
  }

  toggleExpand(id: string): void {
    this.expandedId.update((cur) => (cur === id ? null : id));
  }

  exportCsv(): void {
    const filters: AuditFilters = {};
    if (this.filterUsername()) filters.username = this.filterUsername();
    if (this.filterModule())   filters.module   = this.filterModule();
    if (this.filterAction())   filters.action   = this.filterAction();
    if (this.filterFrom())     filters.from     = this.filterFrom();
    if (this.filterTo())       filters.to       = this.filterTo();

    this.auditService.exportCsv(filters).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMsg.set('Erro ao exportar CSV.');
      },
    });
  }

  formatJson(value: string | null): string {
    if (!value) return '';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }

  formatTimestamp(ts: string): string {
    try {
      return new Date(ts).toLocaleString('pt-BR');
    } catch {
      return ts;
    }
  }

  // ── Retenção ────────────────────────────────────────────────────────────────

  private loadRetention(): void {
    this.auditService.getRetention().subscribe({
      next: (cfg) => {
        this.retention.set(cfg);
        this.retentionDays.set(cfg.retentionDays);
      },
      error: () => { /* ignorar silenciosamente */ },
    });
  }

  saveRetention(): void {
    const days = this.retentionDays();
    if (!this.retentionValid() || days == null) return;
    this.isSavingRetention.set(true);
    this.retentionError.set(null);
    this.auditService.updateRetention(days).subscribe({
      next: (cfg) => {
        this.isSavingRetention.set(false);
        this.retention.set(cfg);
        this.retentionSuccess.set('Retenção atualizada com sucesso.');
        setTimeout(() => this.retentionSuccess.set(null), 3000);
      },
      error: (err: { error?: { message?: string } }) => {
        this.isSavingRetention.set(false);
        this.retentionError.set(err?.error?.message ?? 'Erro ao atualizar retenção.');
      },
    });
  }
}
