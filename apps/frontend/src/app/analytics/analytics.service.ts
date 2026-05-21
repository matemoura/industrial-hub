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

export interface BenchmarkEntry {
  label: string;
  avgOee: number;
  minOee: number;
  maxOee: number;
  stdDev: number | null;
  sampleCount: number;
  recordsWithoutShift?: number | null;
}

export interface BenchmarkResponse {
  ranking: BenchmarkEntry[];
  best: BenchmarkEntry | null;
  worst: BenchmarkEntry | null;
  overallAvg: number;
  recordsWithoutShift?: number | null;
}

export interface PeriodComparisonResponse {
  periodA: BenchmarkEntry[];
  periodB: BenchmarkEntry[];
  improvementPct: number | null;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/analytics';

  getOeeTrend(weeks: number, excludePlannedDowntime = false, shiftId?: string): Observable<OeeTrendResponse> {
    const params: Record<string, string> = { weeks: weeks.toString() };
    if (excludePlannedDowntime) params['excludePlannedDowntime'] = 'true';
    if (shiftId) params['shiftId'] = shiftId;
    return this.http.get<OeeTrendResponse>(`${this.base}/oee/trend`, { params });
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

  getWoSummary(shiftId?: string): Observable<WoSummaryResponse> {
    const params: Record<string, string> = {};
    if (shiftId) params['shiftId'] = shiftId;
    return this.http.get<WoSummaryResponse>(`${this.base}/maintenance/wo-summary`, { params });
  }

  getBenchmarkWorkers(from: string, to: string): Observable<BenchmarkResponse> {
    return this.http.get<BenchmarkResponse>(`${this.base}/oee/benchmark/workers`, {
      params: { from, to },
    });
  }

  getBenchmarkShifts(from: string, to: string): Observable<BenchmarkResponse> {
    return this.http.get<BenchmarkResponse>(`${this.base}/oee/benchmark/shifts`, {
      params: { from, to },
    });
  }

  getBenchmarkEquipmentType(from: string, to: string): Observable<BenchmarkResponse> {
    return this.http.get<BenchmarkResponse>(`${this.base}/oee/benchmark/equipment-type`, {
      params: { from, to },
    });
  }

  getPeriodComparison(
    fromA: string,
    toA: string,
    fromB: string,
    toB: string,
  ): Observable<PeriodComparisonResponse> {
    return this.http.get<PeriodComparisonResponse>(`${this.base}/oee/benchmark/period-comparison`, {
      params: { from: fromA, to: toA, fromB, toB },
    });
  }
}
