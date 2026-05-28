import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

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

// Backend response shape from GET /tracking/families (status-column based)
interface TrackingItem {
  id: string;
  dynamicsOrderNumber: string;
  productName: string;
  familyName: string | null;
  displayStatus: ProductionOrderDisplayStatus;
  plannedQty: number | null;
  producedQty: number | null;
  dueDate: string | null;
  overdue: boolean;
}
interface TrackingColumn {
  status: ProductionOrderDisplayStatus;
  items: TrackingItem[];
  truncated: boolean;
  total: number;
}
interface ProductionTrackingApiResponse {
  columns: TrackingColumn[];
  lastSyncAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProductionTrackingService {
  private readonly http = inject(HttpClient);
  private readonly BASE = '/api/v1/production/tracking';

  getFamilies(): Observable<FamilyTrackingResponse[]> {
    return this.http
      .get<ProductionTrackingApiResponse>(`${this.BASE}/families`)
      .pipe(map((res) => this.transformToFamilies(res)));
  }

  private transformToFamilies(res: ProductionTrackingApiResponse): FamilyTrackingResponse[] {
    const lastSyncAt = res.lastSyncAt ?? null;
    // Flatten all items from all columns
    const allItems = (res.columns ?? []).flatMap((col) =>
      (col.items ?? []).map((item) => ({ ...item, displayStatus: col.status })),
    );

    // Group by familyName
    const byFamily = new Map<string, TrackingItem[]>();
    for (const item of allItems) {
      const key = item.familyName ?? '(Sem família)';
      const list = byFamily.get(key) ?? [];
      list.push(item);
      byFamily.set(key, list);
    }

    return Array.from(byFamily.entries()).map(([familyName, items]) => ({
      familyId: familyName,
      familyCode: familyName,
      familyName,
      overdueCount: items.filter((i) => i.overdue).length,
      lastSyncAt,
      orders: items.map((item) => ({
        dynamicsOrderNumber: item.dynamicsOrderNumber,
        productCode: '',  // not provided by backend in this endpoint
        productName: item.productName,
        productType: 'FINISHED' as const,
        plannedQty: item.plannedQty ?? 0,
        producedQty: item.producedQty ?? null,
        completionPct: item.plannedQty && item.producedQty != null && item.plannedQty > 0
          ? Math.round((item.producedQty / item.plannedQty) * 100)
          : null,
        dueDate: item.dueDate ?? '',
        overdue: item.overdue,
        displayStatus: item.displayStatus,
        loadNumber: null,
        loadStatus: null,
        plannedPeople: null,
      })),
    }));
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
