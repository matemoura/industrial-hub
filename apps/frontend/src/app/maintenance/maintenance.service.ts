import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export type ScheduleRecurrence = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface ScheduleResponse {
  id: string;
  equipmentId: string;
  equipmentCode: string;
  equipmentName: string;
  title: string;
  description: string | null;
  priority: WorkOrderPriority;
  recurrence: ScheduleRecurrence;
  dayOfWeek: number | null;
  dayOfMonth: number | null;
  nextRunAt: string;
  lastRunAt: string | null;
  active: boolean;
  createdBy: string;
  createdAt: string;
}

export interface CreateSchedulePayload {
  equipmentId: string;
  title: string;
  description?: string;
  priority: WorkOrderPriority;
  recurrence: ScheduleRecurrence;
  dayOfWeek?: number;
  dayOfMonth?: number;
}

export interface UpdateSchedulePayload {
  title: string;
  description?: string;
  priority: WorkOrderPriority;
  recurrence: ScheduleRecurrence;
  dayOfWeek?: number;
  dayOfMonth?: number;
}

export type EquipmentType = 'MACHINE' | 'TOOL' | 'VEHICLE' | 'INFRASTRUCTURE';
export type EquipmentStatus = 'OPERATIONAL' | 'UNDER_MAINTENANCE' | 'DECOMMISSIONED';
export type WorkOrderType = 'CORRECTIVE' | 'PREVENTIVE';
export type WorkOrderPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type WorkOrderStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';

export interface EquipmentResponse {
  id: string;
  code: string;
  name: string;
  location: string | null;
  type: EquipmentType;
  status: EquipmentStatus;
  acquiredAt: string | null;
  active: boolean;
}

export interface WorkOrderResponse {
  id: string;
  equipmentId: string;
  equipmentCode: string;
  equipmentName: string;
  type: WorkOrderType;
  title: string;
  description: string | null;
  priority: WorkOrderPriority;
  status: WorkOrderStatus;
  assignedTo: string | null;
  openedBy: string;
  openedAt: string;
  startedAt: string | null;
  closedAt: string | null;
  scheduleId: string | null;
  shiftId: string | null;
  shiftName: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateEquipmentPayload {
  code: string;
  name: string;
  type: EquipmentType;
  location?: string;
  acquiredAt?: string;
}

export interface UpdateEquipmentPayload {
  name: string;
  type: EquipmentType;
  location?: string;
  acquiredAt?: string;
}

export interface CreateWorkOrderPayload {
  equipmentId: string;
  type: WorkOrderType;
  title: string;
  priority: WorkOrderPriority;
  description?: string;
  assignedTo?: string;
}

export interface WorkOrderMetricsResponse {
  mttr: number | null;
  totalOrders: number;
  openOrders: number;
}

// ─── Spare Parts ──────────────────────────────────────────────────────────────

export interface SparePartResponse {
  id: string;
  code: string;
  name: string;
  category: string | null;
  unit: string | null;
  stockQty: number;
  minStockQty: number;
  active: boolean;
  belowMin: boolean;
}

export interface CreateSparePartPayload {
  code: string;
  name: string;
  category?: string;
  unit?: string;
  stockQty: number;
  minStockQty: number;
}

export interface UpdateSparePartPayload {
  name: string;
  category?: string;
  unit?: string;
  minStockQty: number;
}

export interface UpdateSparePartStockPayload {
  quantity: number;
  reason: string;
}

// ─── Work Order Parts ─────────────────────────────────────────────────────────

export interface WorkOrderPartResponse {
  id: string;
  sparePartId: string;
  sparePartCode: string;
  sparePartName: string;
  quantity: number;
  addedBy: string;
  addedAt: string;
}

export interface AddWorkOrderPartPayload {
  sparePartId: string;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class MaintenanceService {
  private readonly http = inject(HttpClient);
  private readonly equipmentUrl = '/api/v1/maintenance/equipment';
  private readonly workOrderUrl = '/api/v1/maintenance/work-orders';

  // Equipment
  createEquipment(payload: CreateEquipmentPayload): Observable<EquipmentResponse> {
    return this.http.post<EquipmentResponse>(this.equipmentUrl, payload);
  }

  listEquipment(filters?: { type?: EquipmentType; status?: EquipmentStatus }): Observable<EquipmentResponse[]> {
    let params = new HttpParams();
    if (filters?.type) params = params.set('type', filters.type);
    if (filters?.status) params = params.set('status', filters.status);
    return this.http.get<EquipmentResponse[]>(this.equipmentUrl, { params });
  }

  getEquipment(id: string): Observable<EquipmentResponse> {
    return this.http.get<EquipmentResponse>(`${this.equipmentUrl}/${id}`);
  }

  updateEquipment(id: string, payload: UpdateEquipmentPayload): Observable<EquipmentResponse> {
    return this.http.put<EquipmentResponse>(`${this.equipmentUrl}/${id}`, payload);
  }

  deleteEquipment(id: string): Observable<void> {
    return this.http.delete<void>(`${this.equipmentUrl}/${id}`);
  }

  // Work Orders
  createWorkOrder(payload: CreateWorkOrderPayload): Observable<WorkOrderResponse> {
    return this.http.post<WorkOrderResponse>(this.workOrderUrl, payload);
  }

  listWorkOrders(
    filters?: {
      equipmentId?: string;
      type?: WorkOrderType;
      status?: WorkOrderStatus;
      priority?: WorkOrderPriority;
      shiftId?: string;
    },
    page = 0,
  ): Observable<PageResponse<WorkOrderResponse>> {
    let params = new HttpParams().set('page', page.toString());
    if (filters?.equipmentId) params = params.set('equipmentId', filters.equipmentId);
    if (filters?.type) params = params.set('type', filters.type);
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.priority) params = params.set('priority', filters.priority);
    if (filters?.shiftId) params = params.set('shiftId', filters.shiftId);
    return this.http.get<PageResponse<WorkOrderResponse>>(this.workOrderUrl, { params });
  }

  transitionStatus(id: string, status: WorkOrderStatus): Observable<WorkOrderResponse> {
    return this.http.put<WorkOrderResponse>(`${this.workOrderUrl}/${id}/status`, { status });
  }

  getWorkOrderMetrics(equipmentId?: string): Observable<WorkOrderMetricsResponse> {
    let params = new HttpParams();
    if (equipmentId) params = params.set('equipmentId', equipmentId);
    return this.http.get<WorkOrderMetricsResponse>(`${this.workOrderUrl}/metrics`, { params });
  }

  // Schedules
  private readonly scheduleUrl = '/api/v1/maintenance/schedules';

  createSchedule(payload: CreateSchedulePayload): Observable<ScheduleResponse> {
    return this.http.post<ScheduleResponse>(this.scheduleUrl, payload);
  }

  listSchedules(equipmentId?: string): Observable<ScheduleResponse[]> {
    let params = new HttpParams();
    if (equipmentId) params = params.set('equipmentId', equipmentId);
    return this.http.get<ScheduleResponse[]>(this.scheduleUrl, { params });
  }

  getSchedule(id: string): Observable<ScheduleResponse> {
    return this.http.get<ScheduleResponse>(`${this.scheduleUrl}/${id}`);
  }

  updateSchedule(id: string, payload: UpdateSchedulePayload): Observable<ScheduleResponse> {
    return this.http.put<ScheduleResponse>(`${this.scheduleUrl}/${id}`, payload);
  }

  deactivateSchedule(id: string): Observable<void> {
    return this.http.put<void>(`${this.scheduleUrl}/${id}/deactivate`, {});
  }

  // ─── Spare Parts ───────────────────────────────────────────────────────────

  private readonly sparePartUrl = '/api/v1/maintenance/spare-parts';

  listSpareParts(filters?: { category?: string; belowMin?: boolean }): Observable<SparePartResponse[]> {
    let params = new HttpParams();
    if (filters?.category) params = params.set('category', filters.category);
    if (filters?.belowMin) params = params.set('belowMin', 'true');
    return this.http.get<SparePartResponse[]>(this.sparePartUrl, { params });
  }

  getSparePart(id: string): Observable<SparePartResponse> {
    return this.http.get<SparePartResponse>(`${this.sparePartUrl}/${id}`);
  }

  createSparePart(payload: CreateSparePartPayload): Observable<SparePartResponse> {
    return this.http.post<SparePartResponse>(this.sparePartUrl, payload);
  }

  updateSparePart(id: string, payload: UpdateSparePartPayload): Observable<SparePartResponse> {
    return this.http.put<SparePartResponse>(`${this.sparePartUrl}/${id}`, payload);
  }

  adjustStock(id: string, payload: UpdateSparePartStockPayload): Observable<SparePartResponse> {
    return this.http.put<SparePartResponse>(`${this.sparePartUrl}/${id}/stock`, payload);
  }

  listBelowMin(): Observable<SparePartResponse[]> {
    return this.listSpareParts({ belowMin: true });
  }

  countBelowMin(): Observable<number> {
    return this.listBelowMin().pipe(map((arr) => arr.length));
  }

  // ─── Work Order Parts ──────────────────────────────────────────────────────

  listWorkOrderParts(workOrderId: string): Observable<WorkOrderPartResponse[]> {
    return this.http.get<WorkOrderPartResponse[]>(`${this.workOrderUrl}/${workOrderId}/parts`);
  }

  addWorkOrderPart(workOrderId: string, payload: AddWorkOrderPartPayload): Observable<WorkOrderPartResponse> {
    return this.http.post<WorkOrderPartResponse>(`${this.workOrderUrl}/${workOrderId}/parts`, payload);
  }

  removeWorkOrderPart(workOrderId: string, partId: string): Observable<void> {
    return this.http.delete<void>(`${this.workOrderUrl}/${workOrderId}/parts/${partId}`);
  }
}
