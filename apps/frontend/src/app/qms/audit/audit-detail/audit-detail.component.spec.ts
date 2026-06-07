import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { AuditDetailComponent } from './audit-detail.component';
import { AuthService } from '../../../auth/auth.service';
import { InternalAuditDetail } from '../../audit.service';

function makeAuth(role: string | null) {
  return { role: signal(role) };
}

function makeActivatedRoute(id: string) {
  return {
    snapshot: { paramMap: { get: (_: string) => id } },
  };
}

function makeAuditDetail(overrides: Partial<InternalAuditDetail> = {}): InternalAuditDetail {
  return {
    id: 'audit-001',
    code: 'AUD-2026-001',
    title: 'Auditoria Teste',
    scope: 'Escopo teste',
    auditType: 'INTERNAL',
    status: 'IN_PROGRESS',
    plannedDate: '2026-06-01',
    completedDate: undefined,
    leadAuditor: 'Ana Silva',
    auditees: ['João', 'Maria'],
    checklistItemsCount: 1,
    findingsCount: 1,
    nonConformingItemsCount: 0,
    createdAt: '2026-06-01T08:00:00',
    checklistItems: [
      {
        id: 'item-001',
        auditId: 'audit-001',
        process: 'Produção',
        isoClause: '7.5.1',
        question: 'O processo está documentado?',
        response: undefined,
        evidence: undefined,
        itemOrder: 1,
      },
    ],
    findings: [
      {
        id: 'finding-001',
        auditId: 'audit-001',
        type: 'NON_CONFORMANCE',
        description: 'Procedimento desatualizado',
        isoClause: '4.2.3',
        severity: 'HIGH',
        linkedNcId: 'nc-uuid-abc123',
        createdAt: '2026-06-02T10:00:00',
      },
    ],
    ...overrides,
  };
}

describe('AuditDetailComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(role: string | null = 'SUPERVISOR', auditId = 'audit-001') {
    TestBed.configureTestingModule({
      imports: [AuditDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: makeAuth(role) },
        { provide: ActivatedRoute, useValue: makeActivatedRoute(auditId) },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AuditDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  function flushAudit(detail: InternalAuditDetail): void {
    httpTesting
      .expectOne((req) => req.url.includes('/qms/audits/'))
      .flush(detail);
  }

  // (a) Select de resposta dispara updateChecklistItem com debounce 800ms
  it('response_select_fires_updateChecklistItem_after_debounce', () => {
    vi.useFakeTimers();
    try {
      const fixture = setup('SUPERVISOR');
      flushAudit(makeAuditDetail());
      fixture.detectChanges();

      const comp = fixture.componentInstance;
      const item = comp.audit()!.checklistItems[0];
      comp.onResponseChange(item, 'CONFORMING');

      // Before 800ms — no PUT request
      vi.advanceTimersByTime(400);
      const early = httpTesting.match((req) => req.method === 'PUT' && req.url.includes('/checklist/'));
      expect(early.length).toBe(0);

      // After 800ms total — PUT fires
      vi.advanceTimersByTime(400);
      const req = httpTesting.expectOne(
        (r) => r.method === 'PUT' && r.url.includes('/checklist/item-001')
      );
      expect(req.request.body.response).toBe('CONFORMING');
      req.flush({ id: 'item-001', auditId: 'audit-001', process: 'Produção', isoClause: '7.5.1', question: 'Q?', response: 'CONFORMING', itemOrder: 1 });

      fixture.destroy();
    } finally {
      vi.useRealTimers();
    }
  });

  // (b) Botão "Gerar PDF" oculto quando status !== 'COMPLETED'
  it('pdf_button_hidden_when_not_completed', () => {
    const fixture = setup('SUPERVISOR');
    flushAudit(makeAuditDetail({ status: 'IN_PROGRESS' }));
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="btn-generate-pdf"]');
    expect(btn).toBeNull();
  });

  // (c) Achado com linkedNcId exibe link para /qms/non-conformances/{id}
  it('finding_with_linkedNcId_shows_link', () => {
    const fixture = setup('SUPERVISOR');
    flushAudit(makeAuditDetail({ status: 'COMPLETED' }));
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('[data-testid="nc-link-finding-001"]');
    expect(link).not.toBeNull();
    expect(link.getAttribute('href')).toContain('non-conformances/nc-uuid-abc123');
  });

  // (d) Botão "Cancelar Auditoria" visível apenas quando status === 'PLANNED'
  it('cancel_button_visible_only_when_planned', () => {
    // PLANNED → botão visível
    const fixture = setup('SUPERVISOR');
    flushAudit(makeAuditDetail({ status: 'PLANNED', checklistItems: [], findings: [] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="btn-cancel-audit"]')).not.toBeNull();

    // Update audit status to IN_PROGRESS via signal
    fixture.componentInstance.audit.update((a) => (a ? { ...a, status: 'IN_PROGRESS' } : a));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="btn-cancel-audit"]')).toBeNull();
  });
});
