import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

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
    },
    page = 0,
  ): Observable<PageResponse<WorkOrderResponse>> {
    let params = new HttpParams().set('page', page.toString());
    if (filters?.equipmentId) params = params.set('equipmentId', filters.equipmentId);
    if (filters?.type) params = params.set('type', filters.type);
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.priority) params = params.set('priority', filters.priority);
    return this.http.get<PageResponse<WorkOrderResponse>>(this.workOrderUrl, { params });
  }

  transitionStatus(id: string, status: WorkOrderStatus): Observable<WorkOrderResponse> {
    return this.http.put<WorkOrderResponse>(`${this.workOrderUrl}/${id}/status`, { status });
  }
}
