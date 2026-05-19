import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

const BASE = '/api/v1/admin/alert-thresholds';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

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
}
