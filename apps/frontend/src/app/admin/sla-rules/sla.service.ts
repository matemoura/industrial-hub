import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type SlaEntityType = 'NC' | 'WORK_ORDER';
export type SlaClassifierField = 'SEVERITY' | 'PRIORITY';

export interface SlaRuleResponse {
  id: string;
  entityType: SlaEntityType;
  classifierField: SlaClassifierField;
  classifierValue: string;
  slaHours: number;
  escalateByEmail: boolean;
  active: boolean;
}

export interface CreateSlaRuleRequest {
  entityType: SlaEntityType;
  classifierField: SlaClassifierField;
  classifierValue: string;
  slaHours: number;
  escalateByEmail: boolean;
}

export interface UpdateSlaRuleRequest {
  slaHours: number;
  escalateByEmail: boolean;
}

export interface EscalationResult {
  breachedNcs: number;
  breachedWorkOrders: number;
}

export interface SlaSummaryResponse {
  totalBreachedNcs: number;
  totalBreachedWorkOrders: number;
  totalOpenNcs: number;
  totalOpenWorkOrders: number;
}

@Injectable({ providedIn: 'root' })
export class SlaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/admin/sla-rules';

  listSlaRules(): Observable<SlaRuleResponse[]> {
    return this.http.get<SlaRuleResponse[]>(this.baseUrl);
  }

  createSlaRule(req: CreateSlaRuleRequest): Observable<SlaRuleResponse> {
    return this.http.post<SlaRuleResponse>(this.baseUrl, req);
  }

  updateSlaRule(id: string, req: UpdateSlaRuleRequest): Observable<SlaRuleResponse> {
    return this.http.put<SlaRuleResponse>(`${this.baseUrl}/${id}`, req);
  }

  deleteSlaRule(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  runEscalationNow(): Observable<EscalationResult> {
    return this.http.post<EscalationResult>(`${this.baseUrl}/run-now`, {});
  }

  getSlaSummary(): Observable<SlaSummaryResponse> {
    return this.http.get<SlaSummaryResponse>(`${this.baseUrl}/summary`);
  }
}
