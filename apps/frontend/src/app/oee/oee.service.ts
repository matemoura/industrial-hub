import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

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

export type GroupBy = 'DAY' | 'WEEK' | 'MONTH';

@Injectable({ providedIn: 'root' })
export class OeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/oee';

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

  getSummary(startDate: string, endDate: string, groupBy: GroupBy = 'DAY'): Observable<PeriodSummaryDto[]> {
    const params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate)
      .set('groupBy', groupBy);
    return this.http.get<PeriodSummaryDto[]>(`${this.baseUrl}/summary`, { params });
  }
}
