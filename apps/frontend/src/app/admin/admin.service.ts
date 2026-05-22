import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// ─── LGPD / Data Retention ───────────────────────────────────────────────────

export interface AnonymizeResponse {
  anonymized: boolean;
  affectedEntities: {
    auditLogs: number;
    nonConformances: number;
    workOrders: number;
  };
}

export interface RetentionPreviewItem {
  entityId: string;
  username: string;
  reason: string;
  entityType: 'USER' | 'AUDIT_LOG' | 'NON_CONFORMANCE' | 'WORK_ORDER' | 'NOTIFICATION';
}

export interface RetentionPreviewResponse {
  items: RetentionPreviewItem[];
  totalUsers: number;
  totalAuditLogs: number;
  totalNotifications: number;
}

export interface RetentionRunResponse {
  anonymizedUsers: number;
  removedNotifications: number;
  clearedAuditLogs: number;
}

// ─── Shift ────────────────────────────────────────────────────────────────────

export interface Shift {
  id: string;
  name: string;
  startTime: string; // 'HH:mm'
  endTime: string;   // 'HH:mm'
  overnight: boolean;
  active: boolean;
}

export interface CreateShiftPayload {
  name: string;
  startTime: string;
  endTime: string;
  overnight: boolean;
}

export type UpdateShiftPayload = CreateShiftPayload;

// ─── AlertThreshold ──────────────────────────────────────────────────────────

export type AlertMetric =
  | 'OEE_AVG_BELOW'
  | 'NC_CRITICAL_ABOVE'
  | 'WO_URGENT_PENDING_HOURS';

export const ALERT_METRIC_LABELS: Record<AlertMetric, string> = {
  OEE_AVG_BELOW: 'OEE médio abaixo de (%)',
  NC_CRITICAL_ABOVE: 'NCs críticas abertas acima de (un.)',
  WO_URGENT_PENDING_HOURS: 'OSs urgentes abertas há mais de (h)',
};

export interface AlertThreshold {
  id: string;
  metric: AlertMetric;
  threshold: number;
  emailEnabled: boolean;
  active: boolean;
  updatedAt: string;
}

export interface CreateAlertThresholdPayload {
  metric: AlertMetric;
  threshold: number;
  emailEnabled: boolean;
}

export interface UpdateAlertThresholdPayload {
  threshold: number;
  emailEnabled: boolean;
}

const SHIFTS_BASE = '/api/v1/admin/shifts';
const BASE = '/api/v1/admin/alert-thresholds';
const USERS_BASE = '/api/v1/admin/users';
const RETENTION_BASE = '/api/v1/admin/data-retention';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  // ─── Shifts ────────────────────────────────────────────────────────────────

  getShifts(): Observable<Shift[]> {
    return this.http.get<Shift[]>(SHIFTS_BASE);
  }

  createShift(payload: CreateShiftPayload): Observable<Shift> {
    return this.http.post<Shift>(SHIFTS_BASE, payload);
  }

  updateShift(id: string, payload: UpdateShiftPayload): Observable<Shift> {
    return this.http.put<Shift>(`${SHIFTS_BASE}/${id}`, payload);
  }

  deactivateShift(id: string): Observable<void> {
    return this.http.put<void>(`${SHIFTS_BASE}/${id}/deactivate`, {});
  }

  // ─── AlertThresholds ───────────────────────────────────────────────────────

  getThresholds(): Observable<AlertThreshold[]> {
    return this.http.get<AlertThreshold[]>(BASE);
  }

  createThreshold(payload: CreateAlertThresholdPayload): Observable<AlertThreshold> {
    return this.http.post<AlertThreshold>(BASE, payload);
  }

  updateThreshold(id: string, payload: UpdateAlertThresholdPayload): Observable<AlertThreshold> {
    return this.http.put<AlertThreshold>(`${BASE}/${id}`, payload);
  }

  deleteThreshold(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}`);
  }

  // ─── LGPD — Anonimização e Retenção ──────────────────────────────────────

  anonymizeUser(id: string): Observable<AnonymizeResponse> {
    return this.http.post<AnonymizeResponse>(`${USERS_BASE}/${id}/anonymize`, {});
  }

  getRetentionPreview(): Observable<RetentionPreviewResponse> {
    return this.http.get<RetentionPreviewResponse>(`${RETENTION_BASE}/preview`);
  }

  runRetention(): Observable<RetentionRunResponse> {
    return this.http.post<RetentionRunResponse>(`${RETENTION_BASE}/run-now`, {});
  }
}
