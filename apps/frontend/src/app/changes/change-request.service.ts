import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ChangeType = 'PROCESS' | 'DOCUMENT' | 'EQUIPMENT' | 'SOFTWARE' | 'REGULATORY' | 'OTHER';
export type ChangeStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'IMPLEMENTED';
export type ChangeEntityType = 'NON_CONFORMANCE' | 'DOCUMENT' | 'EQUIPMENT' | 'RISK_ITEM';

export interface ChangeRequestLink {
  id: string;
  changeRequestId: string;
  entityType: ChangeEntityType;
  entityId: string;
  linkNote?: string;
  createdAt: string;
}

export interface ChangeRequest {
  id: string;
  code: string;
  title: string;
  description: string;
  changeType: ChangeType;
  justification: string;
  impactAssessment?: string;
  status: ChangeStatus;
  requestedBy: string;
  submittedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  implementedAt?: string;
  rejectionReason?: string;
  createdAt: string;
}

export interface ChangeRequestDetail extends ChangeRequest {
  links: ChangeRequestLink[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateChangeRequest {
  title: string;
  description: string;
  changeType: ChangeType;
  justification: string;
}

export interface UpdateChangeRequest {
  title: string;
  description: string;
  changeType: ChangeType;
  justification: string;
}

export interface ReviewChangeRequest {
  impactAssessment: string;
  recommendApproval: boolean;
}

export interface ApproveChangeRequest {
  approved: boolean;
  rejectionReason?: string;
}

export interface AddLinkRequest {
  entityType: ChangeEntityType;
  entityId: string;
  linkNote?: string;
}

export interface ListChangesParams {
  status?: ChangeStatus;
  changeType?: ChangeType;
  requestedBy?: string;
  pendingForMe?: boolean;
  page?: number;
}

@Injectable({ providedIn: 'root' })
export class ChangeRequestService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/changes';

  listChanges(params?: ListChangesParams): Observable<PageResponse<ChangeRequest>> {
    let httpParams = new HttpParams();
    if (params?.status) httpParams = httpParams.set('status', params.status);
    if (params?.changeType) httpParams = httpParams.set('changeType', params.changeType);
    if (params?.requestedBy) httpParams = httpParams.set('requestedBy', params.requestedBy);
    if (params?.pendingForMe != null) httpParams = httpParams.set('pendingForMe', String(params.pendingForMe));
    if (params?.page != null) httpParams = httpParams.set('page', String(params.page));
    return this.http.get<PageResponse<ChangeRequest>>(this.base, { params: httpParams });
  }

  getChange(id: string): Observable<ChangeRequestDetail> {
    return this.http.get<ChangeRequestDetail>(`${this.base}/${id}`);
  }

  createChange(req: CreateChangeRequest): Observable<ChangeRequest> {
    return this.http.post<ChangeRequest>(this.base, req);
  }

  updateChange(id: string, req: UpdateChangeRequest): Observable<ChangeRequest> {
    return this.http.put<ChangeRequest>(`${this.base}/${id}`, req);
  }

  submitChange(id: string): Observable<ChangeRequest> {
    return this.http.post<ChangeRequest>(`${this.base}/${id}/submit`, {});
  }

  reviewChange(id: string, req: ReviewChangeRequest): Observable<ChangeRequest> {
    return this.http.put<ChangeRequest>(`${this.base}/${id}/review`, req);
  }

  approveChange(id: string, req: ApproveChangeRequest): Observable<ChangeRequest> {
    return this.http.put<ChangeRequest>(`${this.base}/${id}/approve`, req);
  }

  implementChange(id: string): Observable<ChangeRequest> {
    return this.http.put<ChangeRequest>(`${this.base}/${id}/implement`, {});
  }

  addLink(id: string, req: AddLinkRequest): Observable<ChangeRequestLink> {
    return this.http.post<ChangeRequestLink>(`${this.base}/${id}/links`, req);
  }

  removeLink(id: string, linkId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/links/${linkId}`);
  }

  countPendingForMe(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/count-pending`);
  }
}
