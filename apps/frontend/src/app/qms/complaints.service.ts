import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ComplaintSource = 'CLIENT' | 'DISTRIBUTOR' | 'REGULATORY_BODY' | 'INTERNAL';
export type ComplaintStatus = 'RECEIVED' | 'UNDER_INVESTIGATION' | 'INVESTIGATION_COMPLETED' | 'CLOSED';
export type NcSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Complaint {
  id: string;
  code: string;
  title: string;
  description: string;
  source: ComplaintSource;
  productCode?: string;
  batchNumber?: string;
  severity: NcSeverity;
  status: ComplaintStatus;
  reportedDate: string;
  reportedBy: string;
  assignedTo: string;
  investigationSummary?: string;
  rootCause?: string;
  correctiveAction?: string;
  reportedToAnvisa: boolean;
  anvisaReportDate?: string;
  anvisaReportNumber?: string;
  linkedNcId?: string;
  linkedNcCode?: string;
  linkedCapaId?: string;
  linkedCapaDescription?: string;
  createdAt: string;
  updatedAt?: string;
  closedAt?: string;
}

export interface ProductCount {
  productCode: string;
  count: number;
}

export interface ComplaintIndicators {
  totalReceived: number;
  byStatus: Record<ComplaintStatus, number>;
  bySeverity: Record<NcSeverity, number>;
  reportedToAnvisa: number;
  avgResolutionDays?: number;
  byProduct: ProductCount[];
  bySource: Record<ComplaintSource, number>;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateComplaintRequest {
  title: string;
  description: string;
  source: ComplaintSource;
  productCode?: string;
  batchNumber?: string;
  severity: NcSeverity;
  reportedDate: string;
  reportedBy: string;
  assignedTo: string;
}

export interface UpdateComplaintRequest {
  title?: string;
  description?: string;
  productCode?: string;
  batchNumber?: string;
  assignedTo?: string;
  investigationSummary?: string;
  rootCause?: string;
  correctiveAction?: string;
}

@Injectable({ providedIn: 'root' })
export class ComplaintService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/qms/complaints';

  listComplaints(params?: {
    status?: ComplaintStatus;
    severity?: NcSeverity;
    productCode?: string;
    reportedToAnvisa?: boolean;
    page?: number;
  }): Observable<PageResponse<Complaint>> {
    let httpParams = new HttpParams();
    if (params?.status) httpParams = httpParams.set('status', params.status);
    if (params?.severity) httpParams = httpParams.set('severity', params.severity);
    if (params?.productCode) httpParams = httpParams.set('productCode', params.productCode);
    if (params?.reportedToAnvisa !== undefined) httpParams = httpParams.set('reportedToAnvisa', String(params.reportedToAnvisa));
    if (params?.page !== undefined) httpParams = httpParams.set('page', String(params.page));
    return this.http.get<PageResponse<Complaint>>(this.base, { params: httpParams });
  }

  getComplaint(id: string): Observable<Complaint> {
    return this.http.get<Complaint>(`${this.base}/${id}`);
  }

  createComplaint(req: CreateComplaintRequest): Observable<Complaint> {
    return this.http.post<Complaint>(this.base, req);
  }

  updateComplaint(id: string, req: UpdateComplaintRequest): Observable<Complaint> {
    return this.http.put<Complaint>(`${this.base}/${id}`, req);
  }

  updateStatus(id: string, req: { status: ComplaintStatus }): Observable<Complaint> {
    return this.http.put<Complaint>(`${this.base}/${id}/status`, req);
  }

  linkNc(id: string, ncId: string): Observable<Complaint> {
    return this.http.put<Complaint>(`${this.base}/${id}/link-nc`, { ncId });
  }

  linkCapa(id: string, capaId: string): Observable<Complaint> {
    return this.http.put<Complaint>(`${this.base}/${id}/link-capa`, { capaId });
  }

  reportAnvisa(id: string, req: { reportNumber: string; reportDate: string }): Observable<Complaint> {
    return this.http.put<Complaint>(`${this.base}/${id}/anvisa-report`, req);
  }

  getIndicators(from: string, to: string): Observable<ComplaintIndicators> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ComplaintIndicators>(`${this.base}/indicators`, { params });
  }

  generateMdrReport(id: string): Observable<Blob> {
    return this.http.post(`${this.base}/${id}/mdr-report`, {}, { responseType: 'blob' });
  }
}
