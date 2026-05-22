import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../auth/auth.service';
import {
  AdminService,
  RetentionPreviewItem,
  RetentionPreviewResponse,
} from '../admin.service';
import { UserService, UserResponse } from '../user.service';

@Component({
  selector: 'app-admin-lgpd',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LowerCasePipe, FormsModule],
  templateUrl: './admin-lgpd.component.html',
  styleUrl: './admin-lgpd.component.scss',
})
export class AdminLgpdComponent implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);

  readonly currentUsername = computed(() => this.authService.username() ?? '');

  // ── Preview de retenção ────────────────────────────────────────────────────
  readonly previewLoading = signal(true);
  readonly previewError = signal<string | null>(null);
  readonly preview = signal<RetentionPreviewResponse | null>(null);
  readonly previewItems = computed(() => this.preview()?.items ?? []);
  readonly hasItems = computed(() => this.previewItems().length > 0);

  // ── Dialog: Executar Retenção ─────────────────────────────────────────────
  readonly showRunDialog = signal(false);
  readonly runLoading = signal(false);
  readonly runSuccess = signal<string | null>(null);

  // ── Dialog: Anonimizar usuário ────────────────────────────────────────────
  readonly showAnonDialog = signal(false);
  readonly anonTarget = signal<UserResponse | null>(null);
  readonly anonConfirmText = signal('');
  readonly anonLoading = signal(false);
  readonly anonError = signal<string | null>(null);

  // ── Tabela de usuários ────────────────────────────────────────────────────
  readonly usersLoading = signal(true);
  readonly usersError = signal<string | null>(null);
  readonly users = signal<UserResponse[]>([]);

  // ── Snackbar global ───────────────────────────────────────────────────────
  readonly successMsg = signal<string | null>(null);
  readonly errorMsg = signal<string | null>(null);

  readonly anonConfirmValid = computed(
    () => this.anonConfirmText() === this.anonTarget()?.username,
  );

  ngOnInit(): void {
    this.loadPreview();
    this.loadUsers();
  }

  private loadPreview(): void {
    this.previewLoading.set(true);
    this.previewError.set(null);
    this.adminService.getRetentionPreview().subscribe({
      next: (res) => {
        this.preview.set(res);
        this.previewLoading.set(false);
      },
      error: () => {
        this.previewError.set('Erro ao carregar candidatos à retenção.');
        this.previewLoading.set(false);
      },
    });
  }

  private loadUsers(): void {
    this.usersLoading.set(true);
    this.userService.list().subscribe({
      next: (list) => {
        this.users.set(list);
        this.usersLoading.set(false);
      },
      error: () => {
        this.usersError.set('Erro ao carregar usuários.');
        this.usersLoading.set(false);
      },
    });
  }

  // ── Retenção ───────────────────────────────────────────────────────────────

  openRunDialog(): void {
    this.showRunDialog.set(true);
  }

  confirmRunRetention(): void {
    this.runLoading.set(true);
    this.adminService.runRetention().subscribe({
      next: (res) => {
        this.showRunDialog.set(false);
        this.runLoading.set(false);
        const msg = `${res.anonymizedUsers} usuário(s) anonimizado(s), ${res.removedNotifications} notificação(ões) removida(s).`;
        this.showSuccess(msg);
        this.loadPreview();
        this.loadUsers();
      },
      error: (err) => {
        this.showRunDialog.set(false);
        this.runLoading.set(false);
        this.showError(err?.error?.message ?? 'Erro ao executar retenção.');
      },
    });
  }

  // ── Anonimização por linha ─────────────────────────────────────────────────

  isOwnAccount(user: UserResponse): boolean {
    return user.username === this.currentUsername();
  }

  isAlreadyAnonymized(user: UserResponse): boolean {
    return user.username.startsWith('[usuario-');
  }

  openAnonDialog(user: UserResponse): void {
    this.anonTarget.set(user);
    this.anonConfirmText.set('');
    this.anonError.set(null);
    this.showAnonDialog.set(true);
  }

  closeAnonDialog(): void {
    this.showAnonDialog.set(false);
    this.anonTarget.set(null);
    this.anonConfirmText.set('');
    this.anonError.set(null);
  }

  submitAnonymize(): void {
    const user = this.anonTarget();
    if (!user || !this.anonConfirmValid()) return;

    this.anonLoading.set(true);
    this.anonError.set(null);
    this.adminService.anonymizeUser(user.id).subscribe({
      next: (res) => {
        this.anonLoading.set(false);
        this.closeAnonDialog();
        const msg = `Usuário anonimizado. Entidades afetadas: ${res.affectedEntities.auditLogs} logs, ${res.affectedEntities.nonConformances} NCs, ${res.affectedEntities.workOrders} OSs.`;
        this.showSuccess(msg);
        this.loadUsers();
        this.loadPreview();
      },
      error: (err) => {
        this.anonLoading.set(false);
        this.anonError.set(err?.error?.message ?? 'Erro ao anonimizar usuário.');
      },
    });
  }

  entityTypeLabel(type: RetentionPreviewItem['entityType']): string {
    const map: Record<RetentionPreviewItem['entityType'], string> = {
      USER: 'Usuário',
      AUDIT_LOG: 'AuditLog',
      NON_CONFORMANCE: 'NC',
      WORK_ORDER: 'OS',
      NOTIFICATION: 'Notificação',
    };
    return map[type] ?? type;
  }

  private showSuccess(msg: string): void {
    this.errorMsg.set(null);
    this.successMsg.set(msg);
    setTimeout(() => this.successMsg.set(null), 5000);
  }

  private showError(msg: string): void {
    this.errorMsg.set(msg);
    setTimeout(() => this.errorMsg.set(null), 5000);
  }
}
