import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { WebhooksComponent } from './webhooks.component';
import {
  WebhookDelivery,
  WebhookSubscriptionResponse,
  WebhooksService,
} from './webhooks.service';

// ─── Mock data ────────────────────────────────────────────────────────────────

const MOCK_SUBSCRIPTIONS: WebhookSubscriptionResponse[] = [
  {
    id: 'wh-001',
    url: 'https://erp.exemplo.com/webhook',
    hasSecret: true,
    events: ['NC_CREATED', 'NC_CRITICAL_OPENED'],
    active: true,
    description: 'ERP Dynamics',
    createdBy: 'admin',
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: null,
    disabledAt: null,
  },
  {
    id: 'wh-002',
    url: 'https://teams.exemplo.com/webhook',
    hasSecret: false,
    events: ['SLA_BREACHED'],
    active: false,
    description: null,
    createdBy: 'admin',
    createdAt: '2026-05-21T08:00:00Z',
    updatedAt: null,
    disabledAt: '2026-05-22T09:00:00Z',
  },
];

const MOCK_DELIVERIES: WebhookDelivery[] = [
  {
    id: 'del-001',
    event: 'NC_CREATED',
    attempt: 1,
    responseCode: 200,
    durationMs: 234,
    status: 'SUCCESS',
    errorMessage: null,
    createdAt: '2026-05-25T10:00:00Z',
  },
  {
    id: 'del-002',
    event: 'NC_CRITICAL_OPENED',
    attempt: 2,
    responseCode: 503,
    durationMs: 5002,
    status: 'FAILED',
    errorMessage: 'Service unavailable',
    createdAt: '2026-05-25T09:00:00Z',
  },
];

// ─── Service factory ──────────────────────────────────────────────────────────

function makeService(
  subs: WebhookSubscriptionResponse[] = MOCK_SUBSCRIPTIONS,
) {
  return {
    list:          vi.fn().mockReturnValue(of(subs)),
    create:        vi.fn().mockReturnValue(of({ ...MOCK_SUBSCRIPTIONS[0], id: 'new-001' })),
    update:        vi.fn().mockReturnValue(of(MOCK_SUBSCRIPTIONS[0])),
    delete:        vi.fn().mockReturnValue(of(undefined)),
    test:          vi.fn().mockReturnValue(of({ url: MOCK_SUBSCRIPTIONS[0].url, responseCode: 200, durationMs: 245, success: true, errorMessage: null })),
    getDeliveries: vi.fn().mockReturnValue(of(MOCK_DELIVERIES)),
    activate:      vi.fn().mockReturnValue(of({ ...MOCK_SUBSCRIPTIONS[1], active: true, disabledAt: null })),
  };
}

// ─── Helper ───────────────────────────────────────────────────────────────────

async function setup(
  subs: WebhookSubscriptionResponse[] = MOCK_SUBSCRIPTIONS,
): Promise<{
  fixture: ComponentFixture<WebhooksComponent>;
  component: WebhooksComponent;
  service: ReturnType<typeof makeService>;
}> {
  const service = makeService(subs);

  TestBed.configureTestingModule({
    imports: [WebhooksComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: WebhooksService, useValue: service },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(WebhooksComponent);
  const component = fixture.componentInstance;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, component, service };
}

// ─── Specs ────────────────────────────────────────────────────────────────────

describe('WebhooksComponent', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  // ─── Spec 1: tabela com N linhas ───────────────────────────────────────────

  describe('Spec 1 — tabela renderiza N linhas com dados mockados', () => {
    it('should render the webhooks table', async () => {
      const { fixture } = await setup();
      const table = fixture.nativeElement.querySelector('[data-testid="webhooks-table"]');
      expect(table).toBeTruthy();
    });

    it('should render one row per subscription', async () => {
      const { fixture } = await setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="webhook-row"]');
      expect(rows.length).toBe(MOCK_SUBSCRIPTIONS.length);
    });

    it('should display the URL in each row', async () => {
      const { fixture } = await setup();
      const urls = fixture.nativeElement.querySelectorAll('[data-testid="webhook-url"]');
      expect(urls.length).toBe(MOCK_SUBSCRIPTIONS.length);
      expect((urls[0] as HTMLElement).textContent?.trim()).toContain('erp.exemplo.com');
    });

    it('should display active chip for active subscription', async () => {
      const { fixture } = await setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="webhook-row"]');
      expect((rows[0] as HTMLElement).textContent).toContain('Ativo');
    });

    it('should display inactive chip for inactive subscription', async () => {
      const { fixture } = await setup();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="webhook-row"]');
      expect((rows[1] as HTMLElement).textContent).toContain('Inativo');
    });
  });

  // ─── Spec 2: estado vazio ──────────────────────────────────────────────────

  describe('Spec 2 — estado vazio quando lista está vazia', () => {
    it('should show empty state when no subscriptions', async () => {
      const { fixture } = await setup([]);
      const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
      expect(empty).toBeTruthy();
    });

    it('should NOT show table when list is empty', async () => {
      const { fixture } = await setup([]);
      const table = fixture.nativeElement.querySelector('[data-testid="webhooks-table"]');
      expect(table).toBeNull();
    });

    it('should show "Nova Subscription" button in empty state', async () => {
      const { fixture } = await setup([]);
      const btn = fixture.nativeElement.querySelector('[data-testid="empty-state"] button');
      expect(btn).toBeTruthy();
    });
  });

  // ─── Spec 3: botão Nova Subscription abre dialog ──────────────────────────

  describe('Spec 3 — botão Nova Subscription abre dialog', () => {
    it('should show dialog when btn-new-webhook is clicked', async () => {
      const { fixture } = await setup();
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-new-webhook"]');
      expect(btn).toBeTruthy();
      btn.click();
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="webhook-dialog"]');
      expect(dialog).toBeTruthy();
    });

    it('should disable submit when URL is empty', async () => {
      const { fixture, component } = await setup();
      component.openCreateDialog();
      fixture.detectChanges();
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-submit-dialog"]');
      expect(btn.disabled).toBe(true);
    });

    it('should disable submit when no events are selected', async () => {
      const { fixture, component } = await setup();
      component.openCreateDialog();
      component.formUrl.set('https://erp.teste.com/webhook');
      fixture.detectChanges();
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-submit-dialog"]');
      expect(btn.disabled).toBe(true);
    });

    it('should enable submit when URL and at least one event are set', async () => {
      const { fixture, component } = await setup();
      component.openCreateDialog();
      component.formUrl.set('https://erp.teste.com/webhook');
      component.toggleEventCheck('NC_CREATED');
      fixture.detectChanges();
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-submit-dialog"]');
      expect(btn.disabled).toBe(false);
    });

    it('should call service.create() when form is valid and submitted', async () => {
      const { fixture, component, service } = await setup();
      component.openCreateDialog();
      component.formUrl.set('https://erp.teste.com/webhook');
      component.toggleEventCheck('NC_CREATED');
      fixture.detectChanges();

      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-submit-dialog"]');
      btn.click();
      fixture.detectChanges();

      expect(service.create).toHaveBeenCalledWith(
        expect.objectContaining({ url: 'https://erp.teste.com/webhook', events: ['NC_CREATED'] }),
      );
    });

    it('should show success message after successful creation', async () => {
      const { fixture, component } = await setup();
      component.openCreateDialog();
      component.formUrl.set('https://erp.teste.com/webhook');
      component.toggleEventCheck('NC_CREATED');
      component.submitForm();
      fixture.detectChanges();

      const success = fixture.nativeElement.querySelector('[data-testid="success-msg"]');
      expect(success).toBeTruthy();
      expect((success as HTMLElement).textContent).toContain('criado');
    });
  });

  // ─── Spec 4: Testar webhook mostra spinner e chama service.test() ──────────

  describe('Spec 4 — botão Testar chama service.test() e exibe spinner', () => {
    it('should call service.test() when test button is clicked', async () => {
      const { fixture, service } = await setup();
      const testBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
        `[data-testid="btn-test-${MOCK_SUBSCRIPTIONS[0].id}"]`,
      );
      expect(testBtn).toBeTruthy();
      testBtn.click();
      fixture.detectChanges();
      expect(service.test).toHaveBeenCalledWith(MOCK_SUBSCRIPTIONS[0].id);
    });

    it('should disable test button while testing', async () => {
      const { fixture, component, service } = await setup();

      // Override to make test async (won't resolve immediately)
      service.test = vi.fn().mockReturnValue(of({ url: '', responseCode: 200, durationMs: 100, success: true, errorMessage: null }));
      component.testingId.set(MOCK_SUBSCRIPTIONS[0].id);
      fixture.detectChanges();

      const testBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
        `[data-testid="btn-test-${MOCK_SUBSCRIPTIONS[0].id}"]`,
      );
      expect(testBtn.disabled).toBe(true);
    });

    it('should show spinner inside test button while testing', async () => {
      const { fixture, component } = await setup();
      component.testingId.set(MOCK_SUBSCRIPTIONS[0].id);
      fixture.detectChanges();
      const spinner = fixture.nativeElement.querySelector(
        `[data-testid="btn-test-${MOCK_SUBSCRIPTIONS[0].id}"] .spinner`,
      );
      expect(spinner).toBeTruthy();
    });

    it('should show success snackbar after successful test', async () => {
      const { fixture, component } = await setup();
      component.testWebhook(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();
      const success = fixture.nativeElement.querySelector('[data-testid="success-msg"]');
      expect(success).toBeTruthy();
      expect((success as HTMLElement).textContent).toContain('200');
    });
  });

  // ─── Spec 5: confirmação de exclusão chama service.delete() ───────────────

  describe('Spec 5 — confirmar exclusão chama service.delete()', () => {
    it('should show delete dialog when delete button is clicked', async () => {
      const { fixture } = await setup();
      const deleteBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
        `[data-testid="btn-delete-${MOCK_SUBSCRIPTIONS[0].id}"]`,
      );
      deleteBtn.click();
      fixture.detectChanges();
      const dialog = fixture.nativeElement.querySelector('[data-testid="delete-dialog"]');
      expect(dialog).toBeTruthy();
    });

    it('should display the webhook URL in the delete confirmation', async () => {
      const { fixture, component } = await setup();
      component.openDeleteConfirm(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();
      const dialog: HTMLElement = fixture.nativeElement.querySelector('[data-testid="delete-dialog"]');
      expect(dialog.textContent).toContain('erp.exemplo.com');
    });

    it('should call service.delete() when confirm button is clicked', async () => {
      const { fixture, component, service } = await setup();
      component.openDeleteConfirm(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();
      const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-confirm-delete"]');
      confirmBtn.click();
      fixture.detectChanges();
      expect(service.delete).toHaveBeenCalledWith(MOCK_SUBSCRIPTIONS[0].id);
    });

    it('should NOT call service.delete() when cancel button is clicked', async () => {
      const { fixture, component, service } = await setup();
      component.openDeleteConfirm(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();
      const cancelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-cancel-delete"]');
      cancelBtn.click();
      fixture.detectChanges();
      expect(service.delete).not.toHaveBeenCalled();
    });

    it('should remove subscription from list after successful delete', async () => {
      const { fixture, component } = await setup();
      component.openDeleteConfirm(MOCK_SUBSCRIPTIONS[0]);
      component.confirmDelete();
      fixture.detectChanges();
      expect(component.subscriptions().find((s) => s.id === MOCK_SUBSCRIPTIONS[0].id)).toBeUndefined();
    });
  });

  // ─── Additional: deliveries panel ─────────────────────────────────────────

  describe('Painel de entregas', () => {
    it('should open deliveries panel when history button is clicked', async () => {
      const { fixture } = await setup();
      const histBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
        `[data-testid="btn-history-${MOCK_SUBSCRIPTIONS[0].id}"]`,
      );
      histBtn.click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="deliveries-panel"]');
      expect(panel).toBeTruthy();
    });

    it('should call getDeliveries with the correct subscription id', async () => {
      const { component, service } = await setup();
      component.openDeliveriesPanel(MOCK_SUBSCRIPTIONS[0]);
      expect(service.getDeliveries).toHaveBeenCalledWith(MOCK_SUBSCRIPTIONS[0].id);
    });

    it('should show deliveries table after data loads', async () => {
      const { fixture, component } = await setup();
      component.openDeliveriesPanel(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();
      const table = fixture.nativeElement.querySelector('[data-testid="deliveries-table"]');
      expect(table).toBeTruthy();
    });

    it('should show empty state when no deliveries', async () => {
      const service = makeService();
      service.getDeliveries = vi.fn().mockReturnValue(of([]));

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [WebhooksComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: WebhooksService, useValue: service },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(WebhooksComponent);
      const component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      component.openDeliveriesPanel(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();

      const empty = fixture.nativeElement.querySelector('[data-testid="deliveries-empty"]');
      expect(empty).toBeTruthy();
    });

    it('should close panel when close button is clicked', async () => {
      const { fixture, component } = await setup();
      component.openDeliveriesPanel(MOCK_SUBSCRIPTIONS[0]);
      fixture.detectChanges();
      const closeBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="btn-close-deliveries"]');
      closeBtn.click();
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[data-testid="deliveries-panel"]');
      expect(panel).toBeNull();
    });
  });

  // ─── Additional: error state ───────────────────────────────────────────────

  describe('Estado de erro', () => {
    it('should show error message when list fails', () => {
      const service = makeService();
      service.list = vi.fn().mockReturnValue(throwError(() => new Error('network error')));

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [WebhooksComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: WebhooksService, useValue: service },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(WebhooksComponent);
      fixture.detectChanges();
      const err = fixture.nativeElement.querySelector('[data-testid="error-msg"]');
      expect(err).toBeTruthy();
    });
  });

  // ─── Additional: reactivate ────────────────────────────────────────────────

  describe('Reativar webhook', () => {
    it('should show reactivate button only for inactive webhooks', async () => {
      const { fixture } = await setup();
      // Active subscription should NOT have activate button
      const activateOnActive = fixture.nativeElement.querySelector(
        `[data-testid="btn-activate-${MOCK_SUBSCRIPTIONS[0].id}"]`,
      );
      expect(activateOnActive).toBeNull();

      // Inactive subscription SHOULD have activate button
      const activateOnInactive = fixture.nativeElement.querySelector(
        `[data-testid="btn-activate-${MOCK_SUBSCRIPTIONS[1].id}"]`,
      );
      expect(activateOnInactive).toBeTruthy();
    });

    it('should call service.activate() when reactivate is clicked', async () => {
      const { fixture, service } = await setup();
      const activateBtn: HTMLButtonElement = fixture.nativeElement.querySelector(
        `[data-testid="btn-activate-${MOCK_SUBSCRIPTIONS[1].id}"]`,
      );
      activateBtn.click();
      fixture.detectChanges();
      expect(service.activate).toHaveBeenCalledWith(MOCK_SUBSCRIPTIONS[1].id);
    });
  });
});
