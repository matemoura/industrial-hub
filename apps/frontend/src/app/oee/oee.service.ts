import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type DowntimeReason = 'PREVENTIVE_MAINTENANCE' | 'SCHEDULED_SETUP' | 'HOLIDAY' | 'OTHER';

export interface PlannedDowntimeResponse {
  id: string;
  equipmentId: string | null;
  equipmentCode: string | null;
  equipmentName: string | null;
  reason: DowntimeReason;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  description: string | null;
  registeredBy: string;
  registeredAt: string;
}

export interface CreateDowntimePayload {
  equipmentId: string | null;
  reason: DowntimeReason;
  startAt: string;
  endAt: string;
  description?: string;
}

export interface ImportResultDto {
  batchId: string;
  periodDate: string;
  workerCount: number;
  recordsImported: number;
}

export interface WorkerDto {
  workerId: number;
  workerName: string;
}

export interface WorkerOeeDto {
  workerId: number;
  workerName: string;
  date: string;
  productiveHours: number | null;
  indirectHours: number | null;
  shiftDuration: number | null;
  availability: number | null;
}

export interface IndirectActivityDto {
  description: string;
  occurrences: number;
  totalHours: number;
  percentOfTotal: number;
}

export interface PeriodSummaryDto {
  period: string;
  avgAvailability: number | null;
  workerCount: number;
}

export interface ProcessEfficiencyDto {
  description: string;
  totalHours: number;
  workerCount: number;
  occurrences: number;
}

export type GroupBy = 'DAY' | 'WEEK' | 'MONTH';

@Injectable({ providedIn: 'root' })
export class OeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/oee';

  getWorkers(): Observable<WorkerDto[]> {
    return this.http.get<WorkerDto[]>('/api/v1/workers');
  }

  getDashboard(
    startDate: string,
    endDate: string,
    workerId?: number,
    excludePlannedDowntime?: boolean,
  ): Observable<WorkerOeeDto[]> {
    let params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    if (workerId != null) params = params.set('workerId', workerId.toString());
    if (excludePlannedDowntime) params = params.set('excludePlannedDowntime', 'true');
    return this.http.get<WorkerOeeDto[]>(`${this.baseUrl}/dashboard`, { params });
  }

  createDowntime(payload: CreateDowntimePayload): Observable<PlannedDowntimeResponse> {
    return this.http.post<PlannedDowntimeResponse>(`${this.baseUrl}/planned-downtimes`, payload);
  }

  listDowntimes(
    equipmentId?: string | null,
    from?: string,
    to?: string,
  ): Observable<PlannedDowntimeResponse[]> {
    let params = new HttpParams();
    if (equipmentId) params = params.set('equipmentId', equipmentId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<PlannedDowntimeResponse[]>(`${this.baseUrl}/planned-downtimes`, { params });
  }

  updateDowntime(id: string, payload: CreateDowntimePayload): Observable<PlannedDowntimeResponse> {
    return this.http.put<PlannedDowntimeResponse>(
      `${this.baseUrl}/planned-downtimes/${id}`,
      payload,
    );
  }

  deleteDowntime(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/planned-downtimes/${id}`);
  }

  getIndirectActivities(
    startDate: string,
    endDate: string,
    workerId?: number,
  ): Observable<IndirectActivityDto[]> {
    let params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    if (workerId != null) params = params.set('workerId', workerId.toString());
    return this.http.get<IndirectActivityDto[]>(`${this.baseUrl}/indirect-activities`, { params });
  }

  exportDashboard(startDate: string, endDate: string, workerId?: number): Observable<Blob> {
    let params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    if (workerId != null) params = params.set('workerId', workerId.toString());
    return this.http.get(`${this.baseUrl}/dashboard/export`, { params, responseType: 'blob' });
  }

  exportSummary(startDate: string, endDate: string, groupBy: GroupBy = 'DAY'): Observable<Blob> {
    const params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate)
      .set('groupBy', groupBy);
    return this.http.get(`${this.baseUrl}/summary/export`, { params, responseType: 'blob' });
  }

  getByProcess(startDate: string, endDate: string, workerId?: number): Observable<ProcessEfficiencyDto[]> {
    let params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    if (workerId != null) params = params.set('workerId', workerId.toString());
    return this.http.get<ProcessEfficiencyDto[]>(`${this.baseUrl}/by-process`, { params });
  }

  getSummary(startDate: string, endDate: string, groupBy: GroupBy = 'DAY'): Observable<PeriodSummaryDto[]> {
    const params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate)
      .set('groupBy', groupBy);
    return this.http.get<PeriodSummaryDto[]>(`${this.baseUrl}/summary`, { params });
  }

  importFile(file: File, overwrite = false): Observable<ImportResultDto> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('overwrite', String(overwrite));
    return this.http.post<ImportResultDto>(`${this.baseUrl}/import`, fd);
  }
}
