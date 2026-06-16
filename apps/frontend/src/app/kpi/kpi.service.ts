import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface KpiSummaryResponse {
  oeeAvgLast30Days: number | null;
  totalNcOpen: number;
  totalNcCritical: number;
  totalWorkOrdersOpen: number;
  mttrGlobalHours: number | null;
  activeEquipmentCount: number;
  totalProductionOrdersOpen: number;
  totalProductionOrdersOverdue: number;
  lastDynamicsSync: string | null;
}

@Injectable({ providedIn: 'root' })
export class KpiService {
  private readonly http = inject(HttpClient);

  getSummary(): Observable<KpiSummaryResponse> {
    return this.http.get<KpiSummaryResponse>('/api/v1/kpi/summary');
  }
}
