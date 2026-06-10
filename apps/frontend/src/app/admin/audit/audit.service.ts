import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AuditLogResponse {
  id: string;
  username: string;
  action: string;
  entityType: string;
  entityId: string;
  module: string | null;
  details: string | null;
  beforeState: string | null;
  afterState: string | null;
  timestamp: string;
  ipAddress: string | null;
}

export interface AuditPage {
  content: AuditLogResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditRetentionConfig {
  retentionDays: number;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface AuditFilters {
  username?: string;
  module?: string;
  action?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

const BASE = '/api/v1/admin/audit';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  getAuditLogs(filters: AuditFilters): Observable<AuditPage> {
    let params = new HttpParams();
    if (filters.username) params = params.set('username', filters.username);
    if (filters.module)   params = params.set('module', filters.module);
    if (filters.action)   params = params.set('action', filters.action);
    if (filters.from)     params = params.set('from', filters.from);
    if (filters.to)       params = params.set('to', filters.to);
    if (filters.page != null) params = params.set('page', String(filters.page));
    if (filters.size != null) params = params.set('size', String(filters.size));
    return this.http.get<AuditPage>(BASE, { params });
  }

  exportCsv(filters: AuditFilters): Observable<Blob> {
    let params = new HttpParams();
    if (filters.username) params = params.set('username', filters.username);
    if (filters.module)   params = params.set('module', filters.module);
    if (filters.action)   params = params.set('action', filters.action);
    if (filters.from)     params = params.set('from', filters.from);
    if (filters.to)       params = params.set('to', filters.to);
    return this.http.get(`${BASE}/export`, { params, responseType: 'blob' });
  }

  getRetention(): Observable<AuditRetentionConfig> {
    return this.http.get<AuditRetentionConfig>(`${BASE}/retention`);
  }

  updateRetention(days: number): Observable<AuditRetentionConfig> {
    return this.http.put<AuditRetentionConfig>(`${BASE}/retention`, { retentionDays: days });
  }
}
