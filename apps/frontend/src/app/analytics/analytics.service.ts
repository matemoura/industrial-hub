import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OeeTrendResponse {
  weekLabels: string[];
  oeeValues: (number | null)[];
  sampleCounts: number[];
}

export interface NcParetoResponse {
  byType: Record<string, number>;
  bySeverity: Record<string, number>;
}

export interface NcTrendResponse {
  labels: string[];
  values: (number | null)[];
}

export interface MttrTrendResponse {
  monthLabels: string[];
  mttrValues: (number | null)[];
}

export interface WoSummaryResponse {
  byStatus: Record<string, number>;
  byType: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/analytics';

  getOeeTrend(weeks: number): Observable<OeeTrendResponse> {
    return this.http.get<OeeTrendResponse>(`${this.base}/oee/trend`, {
      params: { weeks: weeks.toString() },
    });
  }

  getNcPareto(days: number): Observable<NcParetoResponse> {
    return this.http.get<NcParetoResponse>(`${this.base}/nc/pareto`, {
      params: { days: days.toString() },
    });
  }

  getNcTrend(weeks: number): Observable<NcTrendResponse> {
    return this.http.get<NcTrendResponse>(`${this.base}/nc/trend`, {
      params: { weeks: weeks.toString() },
    });
  }

  getMttrTrend(months: number): Observable<MttrTrendResponse> {
    return this.http.get<MttrTrendResponse>(`${this.base}/maintenance/mttr-trend`, {
      params: { months: months.toString() },
    });
  }

  getWoSummary(): Observable<WoSummaryResponse> {
    return this.http.get<WoSummaryResponse>(`${this.base}/maintenance/wo-summary`);
  }
}
