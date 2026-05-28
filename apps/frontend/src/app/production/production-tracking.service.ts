import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ProductionOrderDisplayStatus =
  | 'PLANNED'
  | 'RELEASED'
  | 'IN_PROGRESS'
  | 'PENDING_STERILIZATION'
  | 'IN_LOAD'
  | 'STERILIZING'
  | 'DONE';

export type LoadStatus = 'OPEN' | 'CLOSED' | 'STERILIZING' | 'RELEASED' | 'REJECTED';

export type ProductType = 'FINISHED' | 'INTERMEDIATE' | 'RAW_MATERIAL';

export interface OrderTrackingEntry {
  dynamicsOrderNumber: string;
  productCode: string;
  productName: string;
  productType: ProductType;
  plannedQty: number;
  producedQty: number | null;
  completionPct: number | null;
  dueDate: string;
  overdue: boolean;
  displayStatus: ProductionOrderDisplayStatus;
  loadNumber: string | null;
  loadStatus: LoadStatus | null;
  plannedPeople: number | null;
}

export interface FamilyTrackingResponse {
  familyId: string;
  familyCode: string;
  familyName: string;
  overdueCount: number;
  orders: OrderTrackingEntry[];
  lastSyncAt: string | null;
}

export interface ProductionTrackingSummaryResponse {
  inProgress: number;
  pendingSterilization: number;
  inLoad: number;
  sterilizing: number;
  overdue: number;
  doneThisWeek: number;
  lastSyncAt: string | null;
}

export interface OrderTrackingFilters {
  familyCode?: string;
  displayStatus?: ProductionOrderDisplayStatus;
  overdue?: boolean;
  productType?: ProductType;
}

export interface FlatOrdersResponse {
  orders: OrderTrackingEntry[];
  lastSyncAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProductionTrackingService {
  private readonly http = inject(HttpClient);
  private readonly BASE = '/api/v1/production/tracking';

  getFamilies(): Observable<FamilyTrackingResponse[]> {
    return this.http.get<FamilyTrackingResponse[]>(`${this.BASE}/families`);
  }

  getOrders(filters?: OrderTrackingFilters): Observable<OrderTrackingEntry[]> {
    let params = new HttpParams();
    if (filters?.familyCode != null) params = params.set('familyCode', filters.familyCode);
    if (filters?.displayStatus != null) params = params.set('displayStatus', filters.displayStatus);
    if (filters?.overdue != null) params = params.set('overdue', String(filters.overdue));
    if (filters?.productType != null) params = params.set('productType', filters.productType);
    return this.http.get<OrderTrackingEntry[]>(`${this.BASE}/orders`, { params });
  }

  getSummary(): Observable<ProductionTrackingSummaryResponse> {
    return this.http.get<ProductionTrackingSummaryResponse>(`${this.BASE}/summary`);
  }
}
