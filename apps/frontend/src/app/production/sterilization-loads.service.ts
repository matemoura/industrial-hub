import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

const BASE = '/api/v1/production/sterilization-loads';

export type LoadStatus = 'OPEN' | 'CLOSED' | 'STERILIZING' | 'RELEASED' | 'REJECTED';
export type SterilizationMethod = 'EO_GAS' | 'GAMMA' | 'STEAM' | 'OTHER';

export interface SterilizationLoadSummary {
  id: string;
  loadNumber: string;
  status: LoadStatus;
  method: SterilizationMethod | null;
  sterilizerName: string | null;
  sterilizationDate: string | null;
  batchCode: string | null;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  closedAt: string | null;
  releasedAt: string | null;
  totalOrders: number;  // US-100 — ADR-043 Decisão 5 via @Formula
}

export interface AllocatedOrderEntry {
  id: string;
  dynamicsOrderNumber: string;
  productCode: string;
  productName: string;
  familyName: string | null;
  plannedQty: number | null;
  dueDate: string | null;
  overdue: boolean;
}

export interface SterilizationLoadDetail extends SterilizationLoadSummary {
  orders: AllocatedOrderEntry[];
  totalOrders: number;
  totalPlannedQty: number;
}

export interface PendingOrder {
  id: string;
  dynamicsOrderNumber: string;
  productCode: string;
  productName: string;
  familyName: string | null;
  plannedQty: number | null;
  dueDate: string | null;
  overdue: boolean;
}

export interface CreateLoadBody {
  sterilizerId?: string;
  method?: SterilizationMethod;
  sterilizationDate?: string;
  batchCode?: string;
  notes?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class SterilizationLoadsService {
  private readonly http = inject(HttpClient);

  listLoads(filters?: { status?: LoadStatus; method?: SterilizationMethod }): Observable<PageResponse<SterilizationLoadSummary>> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.method) params = params.set('method', filters.method);
    return this.http.get<PageResponse<SterilizationLoadSummary>>(BASE, { params });
  }

  getLoad(id: string): Observable<SterilizationLoadDetail> {
    return this.http.get<SterilizationLoadDetail>(`${BASE}/${id}`);
  }

  createLoad(body: CreateLoadBody): Observable<SterilizationLoadSummary> {
    return this.http.post<SterilizationLoadSummary>(BASE, body);
  }

  getPendingOrders(): Observable<PendingOrder[]> {
    return this.http.get<PendingOrder[]>(`${BASE}/pending-orders`);
  }

  addOrder(loadId: string, productionOrderId: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${loadId}/orders`, { productionOrderId });
  }

  removeOrder(loadId: string, orderId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/${loadId}/orders/${orderId}`);
  }

  transitionStatus(loadId: string, targetStatus: LoadStatus): Observable<SterilizationLoadSummary> {
    return this.http.put<SterilizationLoadSummary>(`${BASE}/${loadId}/status`, { targetStatus });
  }
}
