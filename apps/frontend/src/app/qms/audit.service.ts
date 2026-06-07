import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type AuditType = 'INTERNAL' | 'SUPPLIER' | 'PROCESS' | 'SYSTEM';
export type AuditStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type ChecklistResponse = 'CONFORMING' | 'NON_CONFORMING' | 'OBSERVATION' | 'NOT_APPLICABLE';
export type FindingType = 'NON_CONFORMANCE' | 'OBSERVATION' | 'OPPORTUNITY_FOR_IMPROVEMENT';
export type NcSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface InternalAudit {
  id: string;
  code: string;
  title: string;
  scope: string;
  auditType: AuditType;
  status: AuditStatus;
  plannedDate: string;
  completedDate?: string;
  leadAuditor: string;
  auditees: string[];
  checklistItemsCount: number;
  findingsCount: number;
  nonConformingItemsCount: number;
  createdAt: string;
}

export interface AuditChecklistItem {
  id: string;
  auditId: string;
  process: string;
  isoClause: string;
  question: string;
  response?: ChecklistResponse;
  evidence?: string;
  itemOrder: number;
}

export interface AuditFinding {
  id: string;
  auditId: string;
  type: FindingType;
  description: string;
  isoClause: string;
  severity: NcSeverity;
  linkedNcId?: string;
  linkedCapaId?: string;
  createdAt: string;
}

export interface InternalAuditDetail extends InternalAudit {
  checklistItems: AuditChecklistItem[];
  findings: AuditFinding[];
}

export interface AuditComplianceDashboard {
  plannedThisYear: number;
  completedThisYear: number;
  overdueAudits: number;
  openFindings: number;
  findingsByType: Record<FindingType, number>;
  conformityRate: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateAuditRequest {
  title: string;
  scope: string;
  auditType: AuditType;
  plannedDate: string;
  leadAuditor: string;
  auditees: string[];
}

export interface CreateChecklistItemRequest {
  process: string;
  isoClause: string;
  question: string;
}

export interface CreateFindingRequest {
  type: FindingType;
  description: string;
  isoClause: string;
  severity: NcSeverity;
  checklistItemId?: string;
  linkedNcId?: string;
  linkedCapaId?: string;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/qms/audits';

  listAudits(params?: {
    status?: AuditStatus;
    auditType?: AuditType;
    leadAuditor?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<InternalAudit>> {
    let p = new HttpParams();
    if (params?.status) p = p.set('status', params.status);
    if (params?.auditType) p = p.set('auditType', params.auditType);
    if (params?.leadAuditor) p = p.set('leadAuditor', params.leadAuditor);
    if (params?.from) p = p.set('from', params.from);
    if (params?.to) p = p.set('to', params.to);
    if (params?.page !== undefined) p = p.set('page', String(params.page));
    if (params?.size !== undefined) p = p.set('size', String(params.size));
    return this.http.get<PageResponse<InternalAudit>>(this.base, { params: p });
  }

  getAudit(id: string): Observable<InternalAuditDetail> {
    return this.http.get<InternalAuditDetail>(`${this.base}/${id}`);
  }

  createAudit(req: CreateAuditRequest): Observable<InternalAudit> {
    return this.http.post<InternalAudit>(this.base, req);
  }

  updateAudit(id: string, req: Partial<CreateAuditRequest>): Observable<InternalAudit> {
    return this.http.put<InternalAudit>(`${this.base}/${id}`, req);
  }

  updateAuditStatus(id: string, req: { status: AuditStatus; completedDate?: string }): Observable<InternalAudit> {
    return this.http.put<InternalAudit>(`${this.base}/${id}/status`, req);
  }

  addChecklistItems(auditId: string, items: CreateChecklistItemRequest[]): Observable<AuditChecklistItem[]> {
    return this.http.post<AuditChecklistItem[]>(`${this.base}/${auditId}/checklist`, items);
  }

  updateChecklistItem(auditId: string, itemId: string, req: { response: ChecklistResponse; evidence?: string }): Observable<AuditChecklistItem> {
    return this.http.put<AuditChecklistItem>(`${this.base}/${auditId}/checklist/${itemId}`, req);
  }

  addFinding(auditId: string, req: CreateFindingRequest): Observable<AuditFinding> {
    return this.http.post<AuditFinding>(`${this.base}/${auditId}/findings`, req);
  }

  deleteFinding(auditId: string, findingId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${auditId}/findings/${findingId}`);
  }

  generateReport(auditId: string): Observable<Blob> {
    return this.http.post(`${this.base}/${auditId}/report`, {}, { responseType: 'blob' });
  }

  getComplianceDashboard(): Observable<AuditComplianceDashboard> {
    return this.http.get<AuditComplianceDashboard>(`${this.base}/compliance-dashboard`);
  }
}
