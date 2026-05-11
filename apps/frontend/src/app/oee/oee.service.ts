import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

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

  getDashboard(startDate: string, endDate: string, workerId?: number): Observable<WorkerOeeDto[]> {
    let params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    if (workerId != null) params = params.set('workerId', workerId.toString());
    return this.http.get<WorkerOeeDto[]>(`${this.baseUrl}/dashboard`, { params });
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
}
