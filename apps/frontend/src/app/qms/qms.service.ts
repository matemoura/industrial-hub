import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type NcStatus = 'OPEN' | 'IN_ANALYSIS' | 'CLOSED';
export type NcType = 'PROCESS' | 'PRODUCT' | 'SUPPLIER' | 'EQUIPMENT' | 'OTHER';
export type NcSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface NcSummaryItem {
  id: string;
  title: string;
  type: NcType;
  severity: NcSeverity;
  status: NcStatus;
  reportedBy: string;
  reportedAt: string;
}

export interface NcResponse extends NcSummaryItem {
  description: string | null;
  closedAt: string | null;
  closedBy: string | null;
}

export interface NcKpiSummary {
  totalOpen: number;
  totalInAnalysis: number;
  totalClosed: number;
  totalCritical: number;
  totalThisMonth: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateNcPayload {
  title: string;
  type: NcType;
  severity: NcSeverity;
  description?: string;
}

@Injectable({ providedIn: 'root' })
export class QmsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/qms/non-conformances';

  createNc(payload: CreateNcPayload): Observable<NcResponse> {
    return this.http.post<NcResponse>(this.baseUrl, payload);
  }

  listNcs(
    filters?: { status?: NcStatus; severity?: NcSeverity; type?: NcType },
    page = 0,
  ): Observable<PageResponse<NcSummaryItem>> {
    let params = new HttpParams().set('page', page.toString());
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.severity) params = params.set('severity', filters.severity);
    if (filters?.type) params = params.set('type', filters.type);
    return this.http.get<PageResponse<NcSummaryItem>>(this.baseUrl, { params });
  }

  getNc(id: string): Observable<NcResponse> {
    return this.http.get<NcResponse>(`${this.baseUrl}/${id}`);
  }

  transitionStatus(id: string, status: NcStatus): Observable<NcResponse> {
    return this.http.put<NcResponse>(`${this.baseUrl}/${id}/status`, { status });
  }

  getKpiSummary(): Observable<NcKpiSummary> {
    return this.http.get<NcKpiSummary>(`${this.baseUrl}/summary`);
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, { responseType: 'blob' });
  }
}
