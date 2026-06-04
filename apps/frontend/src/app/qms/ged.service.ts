import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type DocumentCategory = 'SOP' | 'FORM' | 'POLICY' | 'WORK_INSTRUCTION' | 'RECORD';
export type DocumentStatus = 'DRAFT' | 'PUBLISHED' | 'OBSOLETE';

export interface DocumentSummary {
  id: string;
  code: string;
  title: string;
  category: DocumentCategory;
  status: DocumentStatus;
  currentRevisionNumber: string | null;
  updatedAt: string;
}

export interface DocumentRevision {
  id: string;
  revisionNumber: string;
  originalFileName: string;
  fileSizeBytes: number;
  // SEC-128: uploadedBy removed — authorship is internal; not exposed to OPERATOR+ (ADR-041 §7)
  uploadedAt: string;
  changeReason: string;
}

export interface DocumentDetail {
  id: string;
  code: string;
  title: string;
  category: DocumentCategory;
  status: DocumentStatus;
  createdBy: string;
  createdAt: string;
  currentRevision: DocumentRevision | null;
  revisions: DocumentRevision[];
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class GedService {
  private readonly http = inject(HttpClient);
  private readonly BASE = '/api/v1/qms/ged/documents';

  listDocuments(params: {
    category?: string;
    status?: string;
    page?: number;
  }): Observable<Page<DocumentSummary>> {
    let httpParams = new HttpParams();
    if (params.category) {
      httpParams = httpParams.set('category', params.category);
    }
    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params.page !== undefined && params.page !== null) {
      httpParams = httpParams.set('page', String(params.page));
    }
    return this.http.get<Page<DocumentSummary>>(this.BASE, { params: httpParams });
  }

  getDocument(id: string): Observable<DocumentDetail> {
    return this.http.get<DocumentDetail>(`${this.BASE}/${id}`);
  }

  createDocument(form: FormData): Observable<DocumentDetail> {
    return this.http.post<DocumentDetail>(this.BASE, form);
  }

  addRevision(id: string, form: FormData): Observable<DocumentRevision> {
    return this.http.post<DocumentRevision>(`${this.BASE}/${id}/revisions`, form);
  }

  updateStatus(id: string, status: DocumentStatus): Observable<DocumentDetail> {
    return this.http.put<DocumentDetail>(`${this.BASE}/${id}/status`, { status });
  }

  getDownloadUrl(
    id: string,
    revId: string
  ): Observable<{ url: string; expiresInSeconds: number }> {
    return this.http.get<{ url: string; expiresInSeconds: number }>(
      `${this.BASE}/${id}/revisions/${revId}/download`
    );
  }

  // ── US-115: NCs that reference this document ──────────────────────────────

  listDocumentNcLinks(documentId: string): Observable<import('./qms.service').DocumentNcLinkResponse[]> {
    return this.http.get<import('./qms.service').DocumentNcLinkResponse[]>(
      `${this.BASE}/${documentId}/non-conformances`
    );
  }
}
