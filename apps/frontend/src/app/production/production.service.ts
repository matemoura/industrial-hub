import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

// US-104 — Production Overview
export interface BomCoverageDto {
  totalFinishedProducts: number;
  withBom: number;
  withoutBom: number;
  coveragePct: number | null;
}

export interface MrpFulfillmentDto {
  totalSuggestions: number;
  accepted: number;
  rejected: number;
  pending: number;
  fulfillmentPct: number | null;
}

export interface DailyEfficiencyDto {
  date: string;          // LocalDate as ISO string
  avgEfficiency: number;
}

export interface ProductionOverviewDto {
  bomCoverage: BomCoverageDto;
  mrpFulfillment: MrpFulfillmentDto;
  efficiencyTrend: DailyEfficiencyDto[];
  opsByStatus: Record<string, number>;
}

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
  plannedPeople: number | null;   // US-086 — staffing
  peopleOverridden: boolean;       // US-086 — se editado manualmente
}

export interface StaffingResponse {
  id: string;
  dynamicsOrderNumber: string;
  plannedPeople: number | null;
  peopleOverridden: boolean;
}

export interface CycleTime {
  id: string;
  product: Product;
  cycleSecs: number;
  leadTimeDays: number;
  effectiveDate: string;
}

export interface ImportErrorItem {
  line: number;
  message: string;
}

export interface ImportResult {
  imported: number;
  updated: number;
  skipped: number;
  errors: ImportErrorItem[];
}

export interface ImportPermissions {
  canImportProducts: boolean;
  canImportBom: boolean;
  canImportCycleTimes: boolean;
  canImportStock: boolean;
  canImportOrders: boolean;
  canImportOeeData: boolean;
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

// US-101 — BOM types
export interface BomImportResponse {
  totalRecords: number;
  created: number;
  updated: number;
  errors: number;
  importedBy: string;
  importedAt: string;
  errorDetails: Array<{ line: number; message: string }>;
}

export interface BomComponentRow {
  componentCode: string;
  componentName: string;
  quantity: number;
  unit: string;
  level: number;
  productType: 'FINISHED' | 'INTERMEDIATE' | 'RAW_MATERIAL';
}

// US-102 — Planning report types
export interface PlanningSummaryRow {
  familyCode: string;
  familyName: string;
  productCode: string;
  productName: string;
  plannedQty: number;
  producedQty: number;
  efficiency: number | null;
  pendingMrpQty: number;
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

  getImportPermissions(): Observable<ImportPermissions> {
    return this.http.get<ImportPermissions>(`${this.BASE}/import/my-permissions`);
  }

  listCycleTimes(params?: CycleTimeListParams): Observable<CycleTime[]> {
    let p = new HttpParams();
    if (params?.productId != null) p = p.set('productId', params.productId);
    return this.http.get<CycleTime[]>(`${this.BASE}/cycle-times`, { params: p });
  }

  // US-086 — staffing endpoints
  updateStaffing(orderId: string, plannedPeople: number): Observable<StaffingResponse> {
    return this.http.put<StaffingResponse>(`${this.BASE}/production-orders/${orderId}/staffing`, { plannedPeople });
  }

  resetStaffing(orderId: string): Observable<StaffingResponse> {
    return this.http.delete<StaffingResponse>(`${this.BASE}/production-orders/${orderId}/staffing`);
  }

  // US-101 — BOM endpoints
  importBom(file: File): Observable<BomImportResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<BomImportResponse>(`${this.BASE}/import/bom`, form);
  }

  getBomTemplateUrl(): string {
    return `${this.BASE}/import/bom/template`;
  }

  getProductBom(productCode: string): Observable<BomComponentRow[]> {
    return this.http.get<BomComponentRow[]>(`${this.BASE}/products/${productCode}/bom`);
  }

  // US-104 — Production Overview
  getProductionOverview(): Observable<ProductionOverviewDto> {
    return this.http.get<ProductionOverviewDto>(`${this.BASE}/overview`);
  }
}
