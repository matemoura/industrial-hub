import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type NcStatus = 'OPEN' | 'IN_ANALYSIS' | 'CLOSED';
export type NcType = 'PROCESS' | 'PRODUCT' | 'SUPPLIER' | 'EQUIPMENT' | 'OTHER';
export type NcSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ActionStatus = 'PENDING' | 'DONE';

// ── Supplier ──────────────────────────────────────────────────────────────────

export interface SupplierResponse {
  id: string;
  code: string;
  name: string;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  active: boolean;
  onboardedAt: string | null;
}

export interface CreateSupplierPayload {
  code: string;
  name: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  onboardedAt?: string;
}

export interface SupplierQualityScore {
  supplierId: string;
  supplierName: string;
  totalNcs: number;
  criticalNcs: number;
  highNcs: number;
  qualityScore: number;
}

export interface NcSummaryItem {
  id: string;
  title: string;
  type: NcType;
  severity: NcSeverity;
  status: NcStatus;
  reportedBy: string;
  reportedAt: string;
  slaBreached: boolean;
}

export interface ActionResponse {
  id: string;
  ncId: string;
  description: string;
  responsible: string;
  dueDate: string;
  status: ActionStatus;
  completedAt: string | null;
  completedBy: string | null;
}

export interface RcaResponse {
  id: string;
  ncId: string;
  why1: string;
  answer1: string | null;
  why2: string | null;
  answer2: string | null;
  why3: string | null;
  answer3: string | null;
  why4: string | null;
  answer4: string | null;
  why5: string | null;
  answer5: string | null;
  rootCause: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateRcaPayload {
  why1: string;
  answer1?: string;
  why2?: string;
  answer2?: string;
  why3?: string;
  answer3?: string;
  why4?: string;
  answer4?: string;
  why5?: string;
  answer5?: string;
  rootCause?: string;
}

export interface NcResponse extends NcSummaryItem {
  description: string | null;
  closedAt: string | null;
  closedBy: string | null;
  supplierId: string | null;
  supplierName: string | null;
  actions: ActionResponse[];
  rca: RcaResponse | null;
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
  supplierId?: string;
}

export interface CreateActionPayload {
  description: string;
  responsible: string;
  dueDate: string;
}

@Injectable({ providedIn: 'root' })
export class QmsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/qms/non-conformances';

  createNc(payload: CreateNcPayload): Observable<NcResponse> {
    return this.http.post<NcResponse>(this.baseUrl, payload);
  }

  listNcs(
    filters?: { status?: NcStatus; severity?: NcSeverity; type?: NcType; slaBreached?: boolean },
    page = 0,
  ): Observable<PageResponse<NcSummaryItem>> {
    let params = new HttpParams().set('page', page.toString());
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.severity) params = params.set('severity', filters.severity);
    if (filters?.type) params = params.set('type', filters.type);
    if (filters?.slaBreached !== undefined) params = params.set('slaBreached', filters.slaBreached.toString());
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

  createAction(ncId: string, payload: CreateActionPayload): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.baseUrl}/${ncId}/actions`, payload);
  }

  listActions(ncId: string): Observable<ActionResponse[]> {
    return this.http.get<ActionResponse[]>(`${this.baseUrl}/${ncId}/actions`);
  }

  completeAction(ncId: string, actionId: string): Observable<ActionResponse> {
    return this.http.put<ActionResponse>(`${this.baseUrl}/${ncId}/actions/${actionId}/complete`, {});
  }

  deleteAction(ncId: string, actionId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${ncId}/actions/${actionId}`);
  }

  createRca(ncId: string, payload: CreateRcaPayload): Observable<RcaResponse> {
    return this.http.post<RcaResponse>(`${this.baseUrl}/${ncId}/rca`, payload);
  }

  updateRca(ncId: string, payload: CreateRcaPayload): Observable<RcaResponse> {
    return this.http.put<RcaResponse>(`${this.baseUrl}/${ncId}/rca`, payload);
  }

  getRca(ncId: string): Observable<RcaResponse> {
    return this.http.get<RcaResponse>(`${this.baseUrl}/${ncId}/rca`);
  }

  // ── Suppliers ───────────────────────────────────────────────────────────────

  private readonly suppliersUrl = '/api/v1/qms/suppliers';

  listSuppliers(): Observable<SupplierResponse[]> {
    return this.http.get<SupplierResponse[]>(this.suppliersUrl);
  }

  getSupplier(id: string): Observable<SupplierResponse> {
    return this.http.get<SupplierResponse>(`${this.suppliersUrl}/${id}`);
  }

  createSupplier(payload: CreateSupplierPayload): Observable<SupplierResponse> {
    return this.http.post<SupplierResponse>(this.suppliersUrl, payload);
  }

  updateSupplier(id: string, payload: CreateSupplierPayload): Observable<SupplierResponse> {
    return this.http.put<SupplierResponse>(`${this.suppliersUrl}/${id}`, payload);
  }

  deactivateSupplier(id: string): Observable<void> {
    return this.http.put<void>(`${this.suppliersUrl}/${id}/deactivate`, {});
  }

  getSupplierQualityScore(id: string, days = 90): Observable<SupplierQualityScore> {
    return this.http.get<SupplierQualityScore>(`${this.suppliersUrl}/${id}/quality-score`, {
      params: new HttpParams().set('days', days.toString()),
    });
  }

  getSupplierQualityRanking(days = 90): Observable<SupplierQualityScore[]> {
    return this.http.get<SupplierQualityScore[]>(`${this.suppliersUrl}/quality-ranking`, {
      params: new HttpParams().set('days', days.toString()),
    });
  }
}
