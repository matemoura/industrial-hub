import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';

const BASE = '/api/v1/production';

export type MrpOrderStatus = 'SUGGESTED' | 'ACCEPTED' | 'REJECTED' | 'CONVERTED' | 'SUPERSEDED';
export type PlanningStatus = 'OK' | 'ALERT' | 'CRITICAL';

export interface MrpRunResult {
  run: MrpRunSummary;
  suggestions: MrpSuggestion[];
  purchaseNeeds: PurchaseNeed[];
  messages: string[];
  isDryRun: boolean;
}

export interface MrpRunSummary {
  id: string;
  runAt: string;
  runBy: string;
  isDryRun: boolean;
  productsAnalyzed: number;
  suggestionsGenerated: number;
  alreadyOk: number;
}

export interface MrpSuggestion {
  id: string;
  productCode: string;
  productName: string;
  familyName: string | null;
  suggestedQty: number;
  adjustedQty: number | null;
  suggestedStartDate: string | null;
  suggestedDueDate: string | null;
  status: MrpOrderStatus;
  rejectionReason: string | null;
}

export interface PurchaseNeed {
  productCode: string;
  productName: string;
  quantity: number;
  unit: string | null;
}

export interface ProductPlanningRow {
  productCode: string;
  productName: string;
  type: string;
  currentStock: number;
  minStockQty: number;
  stockSnapshotDate: string | null;
  openOrdersQty: number;
  suggestedOrdersQty: number;
  netNeed: number;
  planningStatus: PlanningStatus;
  totalPlannedPeople: number;
  totalOpsOpen: number;
  leadTimeDays: number | null;
  earliestDueDate: string | null;
}

export interface FamilyPlanningBoard {
  familyId: string;
  familyCode: string;
  familyName: string;
  products: ProductPlanningRow[];
}

export interface TimelineEntry {
  orderNumber: string;
  productCode: string;
  productName: string;
  startDate: string | null;
  dueDate: string | null;
  qty: number;
  statusLabel: string;
  isMrpSuggestion: boolean;
  overdue: boolean;
  suggestionId: string | null;  // BUG-2 fix: UUID completo para accept/reject (null para OPs Dynamics)
}

@Injectable({ providedIn: 'root' })
export class PlanningService {
  private readonly http = inject(HttpClient);

  dryRunMrp(): Observable<MrpRunResult> {
    return this.http.post<MrpRunResult>(`${BASE}/mrp/dry-run`, {});
  }

  runMrp(): Observable<MrpRunResult> {
    return this.http.post<MrpRunResult>(`${BASE}/mrp/run`, {});
  }

  getSuggestions(): Observable<MrpSuggestion[]> {
    return this.http.get<MrpSuggestion[]>(`${BASE}/mrp/suggested-orders`);
  }

  acceptSuggestion(id: string, adjustedQty?: number): Observable<MrpSuggestion> {
    return this.http.put<MrpSuggestion>(`${BASE}/mrp/suggested-orders/${id}/accept`,
      adjustedQty != null ? { adjustedQty } : {});
  }

  rejectSuggestion(id: string, reason: string): Observable<MrpSuggestion> {
    return this.http.put<MrpSuggestion>(`${BASE}/mrp/suggested-orders/${id}/reject`, { reason });
  }

  getPlanningBoard(): Observable<FamilyPlanningBoard[]> {
    return this.http.get<FamilyPlanningBoard[]>(`${BASE}/planning/families`);
  }

  getTimeline(familyCode: string, weeks = 8): Observable<TimelineEntry[]> {
    const params = new HttpParams().set('familyCode', familyCode).set('weeks', String(weeks));
    return this.http.get<TimelineEntry[]>(`${BASE}/planning/timeline`, { params });
  }

  getPurchaseNeeds(): Observable<PurchaseNeed[]> {
    return this.http.get<PurchaseNeed[]>(`${BASE}/planning/purchase-needs`).pipe(
      catchError(err => err.status === 404 ? of([]) : throwError(() => err))
    );
  }
}
