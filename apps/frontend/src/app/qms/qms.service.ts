import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type NcStatus = 'OPEN' | 'IN_ANALYSIS' | 'CLOSED';
export type NcType = 'PROCESS' | 'PRODUCT' | 'SUPPLIER' | 'EQUIPMENT' | 'OTHER';
export type NcSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ActionStatus = 'PENDING' | 'PENDING_EFFECTIVENESS' | 'DONE';
export type ActionType = 'CORRECTIVE' | 'PREVENTIVE';

// ── US-115: NC↔GED Link ───────────────────────────────────────────────────────

export type NcDocumentLinkType =
  | 'PROCEDURE_AT_OCCURRENCE'
  | 'CORRECTIVE_REFERENCE'
  | 'OTHER';

export interface NcDocumentLinkResponse {
  linkId: string;
  documentId: string;
  documentCode: string;
  documentTitle: string;
  documentCategory: string;
  documentStatus: string;
  linkType: NcDocumentLinkType;
  linkedAt: string;
}

export interface LinkNcToDocumentPayload {
  documentId: string;
  linkType: NcDocumentLinkType;
}

export interface DocumentNcLinkResponse {
  linkId: string;
  ncId: string;
  ncCode: string;
  ncTitle: string;
  ncSeverity: string;
  ncStatus: string;
  linkType: NcDocumentLinkType;
  linkedAt: string;
}

// ── US-116: CAPA Aging ────────────────────────────────────────────────────────

export interface AgingBucket {
  count: number;
  label: string;
}

export interface AgingBucketOver30 extends AgingBucket {
  overdueCount: number;
}

export interface OverdueBySeverity {
  severity: string;
  overdueCount: number;
}

export interface CapaAgingResponse {
  totalOpen: number;
  overdueCount: number;
  avgResolutionDaysOpen: number;
  noDueDateCount: number;
  bucket0to7: AgingBucket;
  bucket8to15: AgingBucket;
  bucket16to30: AgingBucket;
  bucketOver30: AgingBucketOver30;
  overdueByNcSeverity: OverdueBySeverity[];
}

// ── US-117: Quality Report ────────────────────────────────────────────────────

export type ReportFormat = 'PDF' | 'EXCEL';
export type ReportSection = 'NCS' | 'CAPAS' | 'GED' | 'RCA';

export interface QualityReportRequest {
  from: string;
  to: string;
  format: ReportFormat;
  sections: ReportSection[];
}

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
  type?: ActionType;
  rootCauseConfirmed?: string | null;
  preventiveMeasure?: string | null;
  effectivenessCheckDate?: string | null;
  effectivenessCheckedBy?: string | null;
  effectivenessResult?: string | null;
}

export interface CAPASummary {
  actionId: string;
  ncCode: string;
  ncTitle: string;
  description: string;
  type: ActionType;
  status: ActionStatus | 'PENDING_EFFECTIVENESS';
  responsible: string;
  dueDate: string | null;
  effectivenessCheckDate: string | null;
}

export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
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

  updateCapa(ncId: string, actionId: string, body: {
    type?: ActionType;
    rootCauseConfirmed?: string;
    preventiveMeasure?: string;
    effectivenessCheckDate?: string;
  }): Observable<ActionResponse> {
    return this.http.put<ActionResponse>(`${this.baseUrl}/${ncId}/corrective-actions/${actionId}`, body);
  }

  submitForEffectiveness(ncId: string, actionId: string): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.baseUrl}/${ncId}/actions/${actionId}/submit-for-effectiveness`, {});
  }

  verifyEffectiveness(ncId: string, actionId: string, body: {
    effectivenessResult: string;
    effectivenessCheckedBy: string;
  }): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.baseUrl}/${ncId}/actions/${actionId}/verify-effectiveness`, body);
  }

  listCapas(params: { type?: string; status?: string; page?: number }): Observable<Page<CAPASummary>> {
    let httpParams = new HttpParams();
    if (params.type) httpParams = httpParams.set('type', params.type);
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    return this.http.get<Page<CAPASummary>>('/api/v1/qms/capas', { params: httpParams });
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

  // ── US-115: NC↔GED Link ────────────────────────────────────────────────────

  linkNcToDocument(ncId: string, payload: LinkNcToDocumentPayload): Observable<NcDocumentLinkResponse> {
    return this.http.post<NcDocumentLinkResponse>(`${this.baseUrl}/${ncId}/documents`, payload);
  }

  listNcDocuments(ncId: string): Observable<NcDocumentLinkResponse[]> {
    return this.http.get<NcDocumentLinkResponse[]>(`${this.baseUrl}/${ncId}/documents`);
  }

  unlinkNcDocument(ncId: string, documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${ncId}/documents/${documentId}`);
  }

  // ── US-116: CAPA Aging ─────────────────────────────────────────────────────

  getCapaAging(): Observable<CapaAgingResponse> {
    return this.http.get<CapaAgingResponse>('/api/v1/qms/capas/aging');
  }

  exportCapaAgingCsv(): Observable<Blob> {
    return this.http.get('/api/v1/qms/capas/aging/export', { responseType: 'blob' });
  }

  updateCapaDueDate(capaId: string, dueDate: string): Observable<void> {
    return this.http.put<void>(`/api/v1/qms/capas/${capaId}/due-date`, { dueDate });
  }

  // ── US-117: Quality Report ─────────────────────────────────────────────────

  generateQualityReport(req: QualityReportRequest): Observable<Blob> {
    return this.http.post('/api/v1/qms/reports/quality', req, { responseType: 'blob' });
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
