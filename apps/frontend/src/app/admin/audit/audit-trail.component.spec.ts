import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuditTrailComponent } from './audit-trail.component';
import { AuditLogResponse, AuditPage, AuditRetentionConfig } from './audit.service';
import { AuthService } from '../../auth/auth.service';
import { computed } from '@angular/core';

const MOCK_LOGS: AuditLogResponse[] = [
  {
    id: 'log-1',
    username: 'joao.silva',
    action: 'NC_CREATED',
    entityType: 'NonConformance',
    entityId: 'nc-abc-123',
    module: 'QMS',
    details: null,
    beforeState: '{"status":"OPEN"}',
    afterState: '{"status":"CLOSED"}',
    timestamp: '2026-06-10T08:30:00Z',
    ipAddress: '192.168.1.10',
  },
  {
    id: 'log-2',
    username: 'maria.admin',
    action: 'USER_CREATED',
    entityType: 'User',
    entityId: 'user-xyz',
    module: 'AUTH',
    details: '{"role":"OPERATOR"}',
    beforeState: null,
    afterState: null,
    timestamp: '2026-06-10T09:15:00Z',
    ipAddress: '10.0.0.1',
  },
];

const MOCK_PAGE: AuditPage = {
  content: MOCK_LOGS,
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
};

const MOCK_RETENTION: AuditRetentionConfig = {
  retentionDays: 365,
  updatedAt: '2026-01-01T00:00:00Z',
  updatedBy: 'admin',
};

const MOCK_AUTH_SERVICE = {
  role: computed(() => 'ADMIN' as string | null),
  username: computed(() => 'test-user' as string | null),
  isAuthenticated: computed(() => true),
  mustChangePassword: computed(() => false),
  getToken: () => null,
};

describe('AuditTrailComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditTrailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: MOCK_AUTH_SERVICE },
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function createComponent() {
    const fixture = TestBed.createComponent(AuditTrailComponent);
    fixture.detectChanges();
    // flush GET /api/v1/admin/audit (logs)
    httpTesting.expectOne((req) => req.url === '/api/v1/admin/audit').flush(MOCK_PAGE);
    // flush GET /api/v1/admin/audit/retention
    httpTesting.expectOne('/api/v1/admin/audit/retention').flush(MOCK_RETENTION);
    fixture.detectChanges();
    return fixture;
  }

  // US-142 (a) — renderiza tabela com logs mockados (coluna "Usuário" aparece)
  it('US-142 (a): renders table with mocked logs — username column visible', () => {
    const fixture = createComponent();
    const usernameCells = fixture.nativeElement.querySelectorAll(
      '[data-testid="log-username"]',
    ) as NodeListOf<HTMLElement>;
    expect(usernameCells.length).toBe(2);
    expect(usernameCells[0].textContent?.trim()).toBe('joao.silva');
    expect(usernameCells[1].textContent?.trim()).toBe('maria.admin');
  });

  // US-142 (b) — click em linha expande exibindo beforeState
  it('US-142 (b): clicking a row expands it showing beforeState', () => {
    const fixture = createComponent();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="log-row"]');
    expect(rows.length).toBe(2);

    // click na primeira linha (tem beforeState)
    (rows[0] as HTMLElement).click();
    fixture.detectChanges();

    const beforeStatePre = fixture.nativeElement.querySelector(
      '[data-testid="before-state"]',
    ) as HTMLElement;
    expect(beforeStatePre).toBeTruthy();
    expect(beforeStatePre.textContent).toContain('OPEN');
  });

  // US-142 (c) — botão "Exportar CSV" chama auditService.exportCsv()
  it('US-142 (c): clicking Export CSV calls auditService.exportCsv()', () => {
    const fixture = createComponent();

    const btn = fixture.nativeElement.querySelector(
      '[data-testid="btn-export-csv"]',
    ) as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();

    const req = httpTesting.expectOne((r) => r.url === '/api/v1/admin/audit/export');
    expect(req.request.method).toBe('GET');
    req.flush(new Blob(['col1,col2\nval1,val2'], { type: 'text/csv' }));
  });

  // US-142 (d) — campo retenção com valor inválido (<30) desabilita botão
  it('US-142 (d): retention input with invalid value (<30) disables save button', () => {
    const fixture = createComponent();

    fixture.componentInstance.retentionDays.set(10); // inválido
    fixture.detectChanges();

    const saveBtn = fixture.nativeElement.querySelector(
      '[data-testid="btn-save-retention"]',
    ) as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(true);
  });

  // US-142 (e) — submit de retenção válido chama updateRetention()
  it('US-142 (e): valid retention submit calls updateRetention()', () => {
    const fixture = createComponent();

    fixture.componentInstance.retentionDays.set(180);
    fixture.detectChanges();

    const saveBtn = fixture.nativeElement.querySelector(
      '[data-testid="btn-save-retention"]',
    ) as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(false);
    saveBtn.click();
    fixture.detectChanges();

    const req = httpTesting.expectOne('/api/v1/admin/audit/retention');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ retentionDays: 180 });
    req.flush({ retentionDays: 180, updatedAt: null, updatedBy: null });
  });
});
