import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RiskStatus = 'IDENTIFIED' | 'BEING_MITIGATED' | 'MITIGATED' | 'ACCEPTED';
export type MitigationStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED';

export interface RiskItemSummary {
  id: string;
  process: string;
  rpn: number;
  riskLevel: RiskLevel;
  status: RiskStatus;
}

export interface RiskItem extends RiskItemSummary {
  failureMode: string;
  failureEffect: string;
  failureCause: string;
  severity: number;
  occurrence: number;
  detectability: number;
  owner: string;
  linkedNcId?: string;
  linkedProductCode?: string;
  createdAt: string;
}

export interface MitigationAction {
  id: string;
  riskItemId: string;
  description: string;
  responsible: string;
  targetDate?: string;
  completedAt?: string;
  residualSeverity?: number;
  residualOccurrence?: number;
  residualDetectability?: number;
  residualRpn?: number;
  status: MitigationStatus;
  createdAt: string;
}

export interface RiskItemDetail extends RiskItem {
  mitigationActions: MitigationAction[];
}

export interface RiskMatrixCell {
  severity: number;
  occurrence: number;
  count: number;
  riskLevel: RiskLevel;
}

export interface RiskMatrixResponse {
  cells: RiskMatrixCell[];
}

export interface RiskSummary {
  totalRisks: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  byStatus: Record<RiskStatus, number>;
  avgRpn: number | null;
  topRisks: RiskItemSummary[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export function rpnToRiskLevel(rpn: number): RiskLevel {
  if (rpn <= 30) return 'LOW';
  if (rpn <= 100) return 'MEDIUM';
  if (rpn <= 200) return 'HIGH';
  return 'CRITICAL';
}

@Injectable({ providedIn: 'root' })
export class RiskService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/qms/risks';

  listRisks(params?: {
    status?: RiskStatus;
    riskLevel?: RiskLevel;
    owner?: string;
    linkedNcId?: string;
    linkedProductCode?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<RiskItem>> {
    let p = new HttpParams();
    if (params?.status) p = p.set('status', params.status);
    if (params?.riskLevel) p = p.set('riskLevel', params.riskLevel);
    if (params?.owner) p = p.set('owner', params.owner);
    if (params?.linkedNcId) p = p.set('linkedNcId', params.linkedNcId);
    if (params?.linkedProductCode) p = p.set('linkedProductCode', params.linkedProductCode);
    if (params?.page !== undefined) p = p.set('page', String(params.page));
    if (params?.size !== undefined) p = p.set('size', String(params.size));
    return this.http.get<PageResponse<RiskItem>>(this.base, { params: p });
  }

  getRisk(id: string): Observable<RiskItemDetail> {
    return this.http.get<RiskItemDetail>(`${this.base}/${id}`);
  }

  createRisk(req: {
    process: string;
    failureMode: string;
    failureEffect: string;
    failureCause: string;
    severity: number;
    occurrence: number;
    detectability: number;
    owner: string;
    linkedNcId?: string;
    linkedProductCode?: string;
  }): Observable<RiskItem> {
    return this.http.post<RiskItem>(this.base, req);
  }

  updateRisk(
    id: string,
    req: Partial<{
      process: string;
      failureMode: string;
      failureEffect: string;
      failureCause: string;
      severity: number;
      occurrence: number;
      detectability: number;
      owner: string;
      linkedNcId: string;
      linkedProductCode: string;
    }>
  ): Observable<RiskItem> {
    return this.http.put<RiskItem>(`${this.base}/${id}`, req);
  }

  updateRiskStatus(id: string, req: { status: RiskStatus }): Observable<RiskItem> {
    return this.http.put<RiskItem>(`${this.base}/${id}/status`, req);
  }

  addMitigationAction(
    riskId: string,
    req: { description: string; responsible: string; targetDate?: string }
  ): Observable<MitigationAction> {
    return this.http.post<MitigationAction>(`${this.base}/${riskId}/mitigation-actions`, req);
  }

  updateMitigationAction(
    riskId: string,
    actionId: string,
    req: Partial<{
      description: string;
      responsible: string;
      targetDate: string;
      completedAt: string;
      residualSeverity: number;
      residualOccurrence: number;
      residualDetectability: number;
      status: MitigationStatus;
    }>
  ): Observable<MitigationAction> {
    return this.http.put<MitigationAction>(
      `${this.base}/${riskId}/mitigation-actions/${actionId}`,
      req
    );
  }

  getRiskMatrix(): Observable<RiskMatrixResponse> {
    return this.http.get<RiskMatrixResponse>(`${this.base}/matrix`);
  }

  getRiskSummary(): Observable<RiskSummary> {
    return this.http.get<RiskSummary>(`${this.base}/summary`);
  }
}
