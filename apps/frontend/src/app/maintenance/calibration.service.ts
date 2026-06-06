import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CalibrationSchedule {
  id: string;
  equipmentId: string;
  equipmentCode: string;
  equipmentName: string;
  intervalDays: number;
  lastCalibratedAt?: string;
  nextDueAt: string;
  overdue: boolean;
  externalProvider?: string;
  active: boolean;
}

export interface CalibrationRecord {
  id: string;
  scheduleId: string;
  equipmentCode: string;
  calibratedAt: string;
  result: 'IN_TOLERANCE' | 'OUT_OF_TOLERANCE' | 'ADJUSTED';
  technician: string;
  notes?: string;
  hasCertificate: boolean;
  certificateDocumentId?: string;
  autoNcId?: string;
  // SEC-159: recordedBy removido da API (ADR-049 §4) — disponível apenas via AuditLog (ADMIN)
  recordedAt: string;
}

export interface CalibrationSummary {
  totalSchedules: number;
  overdueCount: number;
  dueSoon14Days: number;
  lastMonthRecords: number;
  outOfToleranceLastMonth: number;
}

@Injectable({ providedIn: 'root' })
export class CalibrationService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/maintenance';

  listSchedules(params?: { equipmentId?: string; overdue?: boolean }): Observable<CalibrationSchedule[]> {
    let p = new HttpParams();
    if (params?.equipmentId) p = p.set('equipmentId', params.equipmentId);
    if (params?.overdue !== undefined) p = p.set('overdue', String(params.overdue));
    return this.http.get<CalibrationSchedule[]>(`${this.base}/calibration-schedules`, { params: p });
  }

  createSchedule(req: { equipmentId: string; intervalDays: number; externalProvider?: string }): Observable<CalibrationSchedule> {
    return this.http.post<CalibrationSchedule>(`${this.base}/calibration-schedules`, req);
  }

  updateSchedule(id: string, req: { intervalDays: number; externalProvider?: string }): Observable<CalibrationSchedule> {
    return this.http.put<CalibrationSchedule>(`${this.base}/calibration-schedules/${id}`, req);
  }

  deactivateSchedule(id: string): Observable<void> {
    return this.http.put<void>(`${this.base}/calibration-schedules/${id}/deactivate`, {});
  }

  listRecords(scheduleId: string): Observable<CalibrationRecord[]> {
    const params = new HttpParams().set('scheduleId', scheduleId);
    return this.http.get<CalibrationRecord[]>(`${this.base}/calibration-records`, { params });
  }

  createRecord(fd: FormData): Observable<CalibrationRecord> {
    return this.http.post<CalibrationRecord>(`${this.base}/calibration-records`, fd);
  }

  getCertificateUrl(recordId: string): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(`${this.base}/calibration-records/${recordId}/certificate`);
  }

  getCalibrationSummary(): Observable<CalibrationSummary> {
    return this.http.get<CalibrationSummary>(`${this.base}/calibration-schedules/summary`);
  }

  runAlertsNow(): Observable<{ alertsSent: number }> {
    return this.http.post<{ alertsSent: number }>(`${this.base}/admin/calibration/alerts/run-now`, {});
  }
}
