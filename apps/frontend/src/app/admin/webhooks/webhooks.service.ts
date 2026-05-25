import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// ─── Enums ───────────────────────────────────────────────────────────────────

export type WebhookEvent =
  | 'NC_CREATED'
  | 'NC_STATUS_CHANGED'
  | 'NC_CRITICAL_OPENED'
  | 'WORK_ORDER_CREATED'
  | 'WORK_ORDER_STATUS_CHANGED'
  | 'EQUIPMENT_DECOMMISSIONED'
  | 'SLA_BREACHED';

export type DeliveryStatus = 'SUCCESS' | 'FAILED' | 'PENDING_RETRY';

// ─── Interfaces ───────────────────────────────────────────────────────────────

export interface WebhookSubscriptionResponse {
  id: string;
  url: string;
  hasSecret: boolean;
  events: WebhookEvent[];
  active: boolean;
  description: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
  disabledAt: string | null;
}

export interface WebhookDelivery {
  id: string;
  event: WebhookEvent;
  attempt: number;
  responseCode: number | null;
  durationMs: number;
  status: DeliveryStatus;
  errorMessage: string | null;
  createdAt: string;
}

export interface WebhookTestResponse {
  url: string;
  responseCode: number | null;
  durationMs: number;
  success: boolean;
  errorMessage: string | null;
}

export interface CreateWebhookRequest {
  url: string;
  secret?: string | null;
  events: WebhookEvent[];
  description?: string | null;
}

export interface UpdateWebhookRequest {
  url: string;
  secret?: string | null;
  events: WebhookEvent[];
  description?: string | null;
  active?: boolean;
}

// ─── Event category metadata ─────────────────────────────────────────────────

export interface WebhookEventMeta {
  value: WebhookEvent;
  label: string;
  category: 'QMS' | 'Manutenção' | 'SLA';
  color: string;
}

export const WEBHOOK_EVENTS: WebhookEventMeta[] = [
  { value: 'NC_CREATED',               label: 'NC Criada',                    category: 'QMS',       color: '#1D4ED8' },
  { value: 'NC_STATUS_CHANGED',         label: 'NC Status Alterado',           category: 'QMS',       color: '#1D4ED8' },
  { value: 'NC_CRITICAL_OPENED',        label: 'NC Crítica Aberta',            category: 'QMS',       color: '#B91C1C' },
  { value: 'WORK_ORDER_CREATED',        label: 'OS Criada',                    category: 'Manutenção', color: '#166534' },
  { value: 'WORK_ORDER_STATUS_CHANGED', label: 'OS Status Alterado',           category: 'Manutenção', color: '#166534' },
  { value: 'EQUIPMENT_DECOMMISSIONED',  label: 'Equipamento Descomissionado',  category: 'Manutenção', color: '#92400E' },
  { value: 'SLA_BREACHED',              label: 'SLA Violado',                  category: 'SLA',       color: '#7C3AED' },
];

// ─── Service ─────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class WebhooksService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/admin/webhooks';

  list(): Observable<WebhookSubscriptionResponse[]> {
    return this.http.get<WebhookSubscriptionResponse[]>(this.baseUrl);
  }

  create(req: CreateWebhookRequest): Observable<WebhookSubscriptionResponse> {
    return this.http.post<WebhookSubscriptionResponse>(this.baseUrl, req);
  }

  update(id: string, req: UpdateWebhookRequest): Observable<WebhookSubscriptionResponse> {
    return this.http.put<WebhookSubscriptionResponse>(`${this.baseUrl}/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  test(id: string): Observable<WebhookTestResponse> {
    return this.http.post<WebhookTestResponse>(`${this.baseUrl}/${id}/test`, {});
  }

  getDeliveries(id: string): Observable<WebhookDelivery[]> {
    return this.http.get<WebhookDelivery[]>(`${this.baseUrl}/${id}/deliveries`);
  }

  activate(id: string): Observable<WebhookSubscriptionResponse> {
    return this.http.put<WebhookSubscriptionResponse>(`${this.baseUrl}/${id}/activate`, {});
  }
}
