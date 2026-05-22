import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AdminLgpdComponent } from './admin-lgpd.component';
import { AdminService, RetentionPreviewResponse, RetentionRunResponse } from '../admin.service';
import { UserService, UserResponse } from '../user.service';
import { AuthService } from '../../auth/auth.service';

const MOCK_PREVIEW: RetentionPreviewResponse = {
  items: [
    { entityId: 'u1', username: 'operador.antigo', reason: 'Inativo há 3 anos', entityType: 'USER' },
    { entityId: 'l1', username: 'operador.antigo', reason: 'AuditLog com 6 anos', entityType: 'AUDIT_LOG' },
  ],
  totalUsers: 1,
  totalAuditLogs: 1,
  totalNotifications: 0,
};

const EMPTY_PREVIEW: RetentionPreviewResponse = {
  items: [],
  totalUsers: 0,
  totalAuditLogs: 0,
  totalNotifications: 0,
};

const MOCK_RUN: RetentionRunResponse = {
  anonymizedUsers: 2,
  removedNotifications: 15,
  clearedAuditLogs: 3,
};

const MOCK_USERS: UserResponse[] = [
  { id: 'u1', username: 'admin', email: 'admin@msb.com', role: 'ADMIN', active: true, mustChangePassword: false },
  { id: 'u2', username: 'op1', email: 'op1@msb.com', role: 'OPERATOR', active: true, mustChangePassword: false },
  { id: 'u3', username: '[usuario-abc123]', email: '', role: 'OPERATOR', active: false, mustChangePassword: false },
];

function makeAdminService(preview = MOCK_PREVIEW, run = MOCK_RUN) {
  return {
    getRetentionPreview: vi.fn().mockReturnValue(of(preview)),
    runRetention: vi.fn().mockReturnValue(of(run)),
    anonymizeUser: vi.fn().mockReturnValue(of({
      anonymized: true,
      affectedEntities: { auditLogs: 2, nonConformances: 1, workOrders: 0 },
    })),
  };
}

function makeUserService(users = MOCK_USERS) {
  return {
    list: vi.fn().mockReturnValue(of(users)),
  };
}

function makeAuthService(username = 'admin') {
  return {
    role: vi.fn().mockReturnValue('ADMIN'),
    username: vi.fn().mockReturnValue(username),
    isAuthenticated: vi.fn().mockReturnValue(true),
  };
}

async function createFixture(
  adminSvc = makeAdminService(),
  userSvc = makeUserService(),
  authSvc = makeAuthService(),
): Promise<ComponentFixture<AdminLgpdComponent>> {
  await TestBed.configureTestingModule({
    imports: [AdminLgpdComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: AdminService, useValue: adminSvc },
      { provide: UserService, useValue: userSvc },
      { provide: AuthService, useValue: authSvc },
    ],
  }).compileComponents();

  const f = TestBed.createComponent(AdminLgpdComponent);
  f.detectChanges();
  return f;
}

describe('AdminLgpdComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  // ── AC#12: tabela exibe dados do preview ───────────────────────────────────
  it('(AC#12-a) tabela de candidatos exibe dados do preview mockado', async () => {
    const f = await createFixture();
    const rows = f.nativeElement.querySelectorAll('[data-testid="preview-row"]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('operador.antigo');
    expect(rows[0].textContent).toContain('Inativo há 3 anos');
  });

  // ── AC#12: empty state quando preview retorna zeros ─────────────────────
  it('(AC#12-b) empty state exibido quando preview retorna lista vazia', async () => {
    const f = await createFixture(makeAdminService(EMPTY_PREVIEW));
    const emptyState = f.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).toBeTruthy();
  });

  // ── AC#13: dialog de confirmação ao clicar "Executar Retenção" ──────────
  it('(AC#13-c) dialog de confirmação exibido ao clicar em Executar Retenção', async () => {
    const f = await createFixture();
    const btn = f.nativeElement.querySelector('[data-testid="btn-run-retention"]');
    btn.click();
    f.detectChanges();
    const dialog = f.nativeElement.querySelector('[data-testid="run-dialog"]');
    expect(dialog).toBeTruthy();
  });

  // ── AC#13: snackbar com resumo após execução ────────────────────────────
  it('(AC#13-d) snackbar com resumo exibido após execução bem-sucedida', async () => {
    const f = await createFixture();
    f.componentInstance.openRunDialog();
    f.detectChanges();
    f.componentInstance.confirmRunRetention();
    f.detectChanges();
    const msg = f.nativeElement.querySelector('[data-testid="success-msg"]');
    expect(msg).toBeTruthy();
    expect(msg.textContent).toContain('2 usuário(s) anonimizado(s)');
  });

  // ── AC#14: botão anonimizar abre dialog com campo de confirmação ─────────
  it('(AC#14) botão Anonimizar abre dialog com campo de username', async () => {
    const f = await createFixture();
    const btn = f.nativeElement.querySelector('[data-testid="btn-anonymize"]');
    expect(btn).toBeTruthy();
    btn.click();
    f.detectChanges();
    const input = f.nativeElement.querySelector('[data-testid="anon-confirm-input"]');
    expect(input).toBeTruthy();
  });

  // ── Botão confirmar desabilitado até digitar username correto ─────────────
  it('botão confirmar-anon desabilitado até username correto ser digitado', async () => {
    const f = await createFixture();
    f.componentInstance.openAnonDialog(MOCK_USERS[1]);
    f.detectChanges();
    const btn = f.nativeElement.querySelector('[data-testid="btn-confirm-anon"]');
    expect(btn.disabled).toBe(true);

    f.componentInstance.anonConfirmText.set('op1');
    f.detectChanges();
    expect(btn.disabled).toBe(false);
  });

  // ── Usuário anonimizado exibe chip "Anonimizado" ──────────────────────────
  it('usuário já anonimizado exibe chip Anonimizado e sem botão Anonimizar', async () => {
    const f = await createFixture();
    const chips = f.nativeElement.querySelectorAll('[data-testid="chip-anonymized"]');
    expect(chips.length).toBe(1);
  });

  // ── Conta própria não exibe botão anonimizar ──────────────────────────────
  it('conta própria do ADMIN não exibe botão Anonimizar', async () => {
    const f = await createFixture(makeAdminService(), makeUserService(), makeAuthService('admin'));
    const btns = f.nativeElement.querySelectorAll('[data-testid="btn-anonymize"]');
    // admin (conta própria) + usuário anon (sem botão) → somente 'op1' tem botão
    expect(btns.length).toBe(1);
    expect(btns[0].getAttribute('aria-label')).toContain('op1');
  });

  // ── Erro 422 exibido no dialog ────────────────────────────────────────────
  it('erro da API exibido no dialog ao anonimizar', async () => {
    const failSvc = {
      ...makeAdminService(),
      anonymizeUser: vi.fn().mockReturnValue(throwError(() => ({
        error: { message: 'Usuário já foi anonimizado' },
      }))),
    };
    const f = await createFixture(failSvc);
    f.componentInstance.openAnonDialog(MOCK_USERS[1]);
    f.componentInstance.anonConfirmText.set('op1');
    f.componentInstance.submitAnonymize();
    f.detectChanges();
    const errEl = f.nativeElement.querySelector('[data-testid="anon-error"]');
    expect(errEl).toBeTruthy();
    expect(errEl.textContent).toContain('Usuário já foi anonimizado');
  });

  // ── entityTypeLabel ────────────────────────────────────────────────────────
  it('entityTypeLabel retorna rótulo correto para cada tipo', async () => {
    const f = await createFixture();
    const c = f.componentInstance;
    expect(c.entityTypeLabel('USER')).toBe('Usuário');
    expect(c.entityTypeLabel('AUDIT_LOG')).toBe('AuditLog');
    expect(c.entityTypeLabel('NON_CONFORMANCE')).toBe('NC');
    expect(c.entityTypeLabel('WORK_ORDER')).toBe('OS');
    expect(c.entityTypeLabel('NOTIFICATION')).toBe('Notificação');
  });
});
