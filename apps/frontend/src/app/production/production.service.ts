import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ProductFamily {
  id: string;
  dynamicsCode: string;
  name: string;
  active: boolean;
}

export interface Product {
  id: string;
  dynamicsCode: string;
  name: string;
  description: string;
  family: ProductFamily;
  active: boolean;
}

export interface StockSnapshot {
  id: string;
  product: Product;
  qty: number;
  unit: string;
  snapshotDate: string;
}

export interface ProductionOrder {
  id: string;
  dynamicsOrderNumber: string;
  product: Product;
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  plannedQty: number;
  producedQty: number | null;
  startDate: string | null;
  dueDate: string;
}

export interface CycleTime {
  id: string;
  product: Product;
  cycleSecs: number;
  leadTimeDays: number;
  effectiveDate: string;
}

export interface ImportResult {
  imported: number;
  updated: number;
  skipped: number;
  errors: string[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface ProductListParams {
  familyId?: string;
  active?: boolean;
  page?: number;
  size?: number;
}

export interface StockListParams {
  productId?: string;
  page?: number;
  size?: number;
}

export interface OrderListParams {
  status?: string;
  familyId?: string;
  page?: number;
  size?: number;
}

export interface CycleTimeListParams {
  productId?: string;
}

@Injectable({ providedIn: 'root' })
export class ProductionService {
  private readonly http = inject(HttpClient);

  private readonly BASE = '/api/v1/production';

  importProducts(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.BASE}/import/products`, form);
  }

  listProducts(params?: ProductListParams): Observable<Page<Product>> {
    let p = new HttpParams();
    if (params?.familyId != null) p = p.set('familyId', params.familyId);
    if (params?.active != null) p = p.set('active', String(params.active));
    if (params?.page != null) p = p.set('page', String(params.page));
    if (params?.size != null) p = p.set('size', String(params.size));
    return this.http.get<Page<Product>>(`${this.BASE}/products`, { params: p });
  }

  listFamilies(): Observable<ProductFamily[]> {
    return this.http.get<ProductFamily[]>(`${this.BASE}/families`);
  }

  importStock(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.BASE}/import/stock`, form);
  }

  listStock(params?: StockListParams): Observable<StockSnapshot[]> {
    let p = new HttpParams();
    if (params?.productId != null) p = p.set('productId', params.productId);
    return this.http.get<StockSnapshot[]>(`${this.BASE}/stock`, { params: p });
  }

  importOrders(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.BASE}/import/production-orders`, form);
  }

  listOrders(params?: OrderListParams): Observable<Page<ProductionOrder>> {
    let p = new HttpParams();
    if (params?.status != null) p = p.set('status', params.status);
    if (params?.familyId != null) p = p.set('familyId', params.familyId);
    if (params?.page != null) p = p.set('page', String(params.page));
    if (params?.size != null) p = p.set('size', String(params.size));
    return this.http.get<Page<ProductionOrder>>(`${this.BASE}/orders`, { params: p });
  }

  importCycleTimes(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.BASE}/import/cycle-times`, form);
  }

  listCycleTimes(params?: CycleTimeListParams): Observable<CycleTime[]> {
    let p = new HttpParams();
    if (params?.productId != null) p = p.set('productId', params.productId);
    return this.http.get<CycleTime[]>(`${this.BASE}/cycle-times`, { params: p });
  }
}
