import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type WidgetType =
  | 'oee-avg'
  | 'nc-open'
  | 'nc-critical'
  | 'wo-open'
  | 'mttr'
  | 'equipment-count'
  | 'oee-trend'
  | 'nc-pareto';

export interface WidgetConfig {
  id: string;
  type: WidgetType;
  column: number;
  row: number;
}

export interface UserDashboardConfigResponse {
  widgetsJson: string;
}

export const WIDGET_LABELS: Record<WidgetType, string> = {
  'oee-avg': 'OEE Médio',
  'nc-open': 'NCs Abertas',
  'nc-critical': 'NCs Críticas',
  'wo-open': 'OSs Abertas',
  'mttr': 'MTTR Global',
  'equipment-count': 'Equipamentos Ativos',
  'oee-trend': 'Tendência OEE',
  'nc-pareto': 'Pareto de NCs',
};

export const SUPERVISOR_ONLY_WIDGETS: WidgetType[] = ['oee-trend', 'nc-pareto'];

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/users/me/dashboard';

  getLayout(): Observable<UserDashboardConfigResponse> {
    return this.http.get<UserDashboardConfigResponse>(this.baseUrl);
  }

  saveLayout(widgetsJson: string): Observable<UserDashboardConfigResponse> {
    return this.http.put<UserDashboardConfigResponse>(this.baseUrl, { widgetsJson });
  }

  deleteLayout(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
