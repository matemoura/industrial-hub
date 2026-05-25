import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  CreateWebhookRequest,
  DeliveryStatus,
  UpdateWebhookRequest,
  WEBHOOK_EVENTS,
  WebhookDelivery,
  WebhookEvent,
  WebhookEventMeta,
  WebhookSubscriptionResponse,
  WebhooksService,
} from './webhooks.service';

type DialogMode = 'create' | 'edit' | null;

interface EventCheckState {
  meta: WebhookEventMeta;
  checked: boolean;
}

@Component({
  selector: 'app-webhooks',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './webhooks.component.html',
  styleUrl: './webhooks.component.scss',
})
export class WebhooksComponent implements OnInit {
  private readonly service = inject(WebhooksService);
  private readonly destroyRef = inject(DestroyRef);

  // ─── State ────────────────────────────────────────────────────────────────

  readonly subscriptions = signal<WebhookSubscriptionResponse[]>([]);
  readonly isLoading = signal(false);
  readonly testingId = signal<string | null>(null);
  readonly deletingId = signal<string | null>(null);
  readonly activatingId = signal<string | null>(null);

  readonly successMsg = signal<string | null>(null);
  readonly errorMsg = signal<string | null>(null);

  // ─── Dialog ───────────────────────────────────────────────────────────────

  readonly dialogMode = signal<DialogMode>(null);
  readonly editTarget = signal<WebhookSubscriptionResponse | null>(null);

  readonly formUrl = signal('');
  readonly formSecret = signal('');
  readonly formDescription = signal('');
  readonly formEvents = signal<EventCheckState[]>(this.buildEventCheckState([]));
  readonly showSecret = signal(false);
  readonly formSubmitting = signal(false);
  readonly formError = signal<string | null>(null);

  // ─── Delete confirm dialog ────────────────────────────────────────────────

  readonly deleteTarget = signal<WebhookSubscriptionResponse | null>(null);

  // ─── Deliveries panel ────────────────────────────────────────────────────

  readonly deliveriesTarget = signal<WebhookSubscriptionResponse | null>(null);
  readonly deliveries = signal<WebhookDelivery[]>([]);
  readonly deliveriesLoading = signal(false);

  // ─── Computed ─────────────────────────────────────────────────────────────

  readonly qmsEvents = computed(() =>
    this.formEvents().filter((e) => e.meta.category === 'QMS'),
  );
  readonly maintenanceEvents = computed(() =>
    this.formEvents().filter((e) => e.meta.category === 'Manutenção'),
  );
  readonly slaEvents = computed(() =>
    this.formEvents().filter((e) => e.meta.category === 'SLA'),
  );

  readonly selectedEventCount = computed(
    () => this.formEvents().filter((e) => e.checked).length,
  );

  readonly isFormValid = computed(
    () => this.formUrl().trim().length > 0 && this.selectedEventCount() > 0,
  );

  // ─── Event metadata lookup ────────────────────────────────────────────────

  readonly eventMetaMap = Object.fromEntries(
    WEBHOOK_EVENTS.map((e) => [e.value, e]),
  ) as Record<WebhookEvent, WebhookEventMeta>;

  // ─── Lifecycle ────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadSubscriptions();
  }

  private loadSubscriptions(): void {
    this.isLoading.set(true);
    this.service
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.subscriptions.set(data);
          this.isLoading.set(false);
        },
        error: () => {
          this.showError('Erro ao carregar webhooks.');
          this.isLoading.set(false);
        },
      });
  }

  // ─── Dialog: create ───────────────────────────────────────────────────────

  openCreateDialog(): void {
    this.formUrl.set('');
    this.formSecret.set('');
    this.formDescription.set('');
    this.formEvents.set(this.buildEventCheckState([]));
    this.showSecret.set(false);
    this.formError.set(null);
    this.editTarget.set(null);
    this.dialogMode.set('create');
  }

  // ─── Dialog: edit ─────────────────────────────────────────────────────────

  openEditDialog(sub: WebhookSubscriptionResponse): void {
    this.formUrl.set(sub.url);
    this.formSecret.set('');
    this.formDescription.set(sub.description ?? '');
    this.formEvents.set(this.buildEventCheckState(sub.events));
    this.showSecret.set(false);
    this.formError.set(null);
    this.editTarget.set(sub);
    this.dialogMode.set('edit');
  }

  closeDialog(): void {
    this.dialogMode.set(null);
    this.formError.set(null);
    this.editTarget.set(null);
  }

  submitForm(): void {
    if (!this.isFormValid()) return;

    const selectedEvents = this.formEvents()
      .filter((e) => e.checked)
      .map((e) => e.meta.value);

    const mode = this.dialogMode();
    this.formSubmitting.set(true);
    this.formError.set(null);

    if (mode === 'create') {
      const req: CreateWebhookRequest = {
        url: this.formUrl().trim(),
        events: selectedEvents,
        description: this.formDescription().trim() || null,
        secret: this.formSecret().trim() || null,
      };
      this.service.create(req).subscribe({
        next: (created) => {
          this.subscriptions.update((list) => [created, ...list]);
          this.formSubmitting.set(false);
          this.closeDialog();
          this.showSuccess('Webhook criado com sucesso.');
        },
        error: (err: { error?: { message?: string } }) => {
          this.formError.set(err?.error?.message ?? 'Erro ao criar webhook.');
          this.formSubmitting.set(false);
        },
      });
    } else if (mode === 'edit') {
      const target = this.editTarget();
      if (!target) return;

      const req: UpdateWebhookRequest = {
        url: this.formUrl().trim(),
        events: selectedEvents,
        description: this.formDescription().trim() || null,
        secret: this.formSecret().trim() || null,
      };
      this.service.update(target.id, req).subscribe({
        next: (updated) => {
          this.subscriptions.update((list) =>
            list.map((s) => (s.id === updated.id ? updated : s)),
          );
          this.formSubmitting.set(false);
          this.closeDialog();
          this.showSuccess('Webhook atualizado com sucesso.');
        },
        error: (err: { error?: { message?: string } }) => {
          this.formError.set(err?.error?.message ?? 'Erro ao atualizar webhook.');
          this.formSubmitting.set(false);
        },
      });
    }
  }

  // ─── Delete confirm ───────────────────────────────────────────────────────

  openDeleteConfirm(sub: WebhookSubscriptionResponse): void {
    this.deleteTarget.set(sub);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;

    this.deletingId.set(target.id);
    this.deleteTarget.set(null);

    this.service.delete(target.id).subscribe({
      next: () => {
        this.subscriptions.update((list) => list.filter((s) => s.id !== target.id));
        this.deletingId.set(null);
        this.showSuccess('Webhook removido.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.deletingId.set(null);
        this.showError(err?.error?.message ?? 'Erro ao remover webhook.');
      },
    });
  }

  // ─── Test ─────────────────────────────────────────────────────────────────

  testWebhook(sub: WebhookSubscriptionResponse): void {
    if (this.testingId() !== null) return;
    this.testingId.set(sub.id);

    this.service.test(sub.id).subscribe({
      next: (result) => {
        this.testingId.set(null);
        if (result.success) {
          this.showSuccess(
            `Sucesso (${result.responseCode ?? '?'} — ${result.durationMs}ms)`,
          );
        } else {
          this.showError(
            result.errorMessage
              ? `Falha: ${result.errorMessage}`
              : `Falha (${result.responseCode ?? 'timeout'} — ${result.durationMs}ms)`,
          );
        }
      },
      error: () => {
        this.testingId.set(null);
        this.showError('Erro ao executar teste do webhook.');
      },
    });
  }

  // ─── Activate ─────────────────────────────────────────────────────────────

  activateWebhook(sub: WebhookSubscriptionResponse): void {
    this.activatingId.set(sub.id);
    this.service.activate(sub.id).subscribe({
      next: (updated) => {
        this.subscriptions.update((list) =>
          list.map((s) => (s.id === updated.id ? updated : s)),
        );
        this.activatingId.set(null);
        this.showSuccess('Webhook reativado.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.activatingId.set(null);
        this.showError(err?.error?.message ?? 'Erro ao reativar webhook.');
      },
    });
  }

  // ─── Deliveries panel ────────────────────────────────────────────────────

  openDeliveriesPanel(sub: WebhookSubscriptionResponse): void {
    this.deliveriesTarget.set(sub);
    this.deliveries.set([]);
    this.deliveriesLoading.set(true);

    this.service
      .getDeliveries(sub.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.deliveries.set(data);
          this.deliveriesLoading.set(false);
        },
        error: () => {
          this.deliveriesLoading.set(false);
        },
      });
  }

  closeDeliveriesPanel(): void {
    this.deliveriesTarget.set(null);
    this.deliveries.set([]);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  toggleEventCheck(value: WebhookEvent): void {
    this.formEvents.update((list) =>
      list.map((e) =>
        e.meta.value === value ? { ...e, checked: !e.checked } : e,
      ),
    );
  }

  truncateUrl(url: string, max = 60): string {
    return url.length <= max ? url : url.slice(0, max) + '…';
  }

  lastDeliveryCode(sub: WebhookSubscriptionResponse): number | null {
    // Backend não retorna a última entrega inline; campo usado quando disponível em cache
    return null;
  }

  deliveryStatusClass(status: DeliveryStatus): string {
    switch (status) {
      case 'SUCCESS':       return 'chip chip--success';
      case 'FAILED':        return 'chip chip--error';
      case 'PENDING_RETRY': return 'chip chip--warning';
    }
  }

  deliveryStatusLabel(status: DeliveryStatus): string {
    switch (status) {
      case 'SUCCESS':       return 'Sucesso';
      case 'FAILED':        return 'Falhou';
      case 'PENDING_RETRY': return 'Aguardando retry';
    }
  }

  responseCodeClass(code: number | null): string {
    if (code === null) return 'http-code http-code--null';
    if (code >= 200 && code < 300) return 'http-code http-code--ok';
    return 'http-code http-code--error';
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  dismissError(): void {
    this.errorMsg.set(null);
  }

  private buildEventCheckState(selected: WebhookEvent[]): EventCheckState[] {
    return WEBHOOK_EVENTS.map((meta) => ({
      meta,
      checked: selected.includes(meta.value),
    }));
  }

  private showSuccess(message: string): void {
    this.successMsg.set(message);
    this.errorMsg.set(null);
    setTimeout(() => this.successMsg.set(null), 4000);
  }

  private showError(message: string): void {
    this.errorMsg.set(message);
    this.successMsg.set(null);
  }
}
