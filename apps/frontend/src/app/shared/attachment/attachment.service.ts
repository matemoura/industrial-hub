import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AttachmentResponse {
  id: string;
  entityType: string;
  entityId: string;
  originalName: string;
  contentType: string;
  fileSizeBytes: number;
  uploadedBy: string;
  uploadedAt: string;
}

export interface DownloadUrlResponse {
  url: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class AttachmentService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/attachments';

  list(entityType: string, entityId: string): Observable<AttachmentResponse[]> {
    return this.http.get<AttachmentResponse[]>(this.base, {
      params: { entityType, entityId }
    });
  }

  upload(entityType: string, entityId: string, file: File): Observable<AttachmentResponse> {
    const form = new FormData();
    form.append('entityType', entityType);
    form.append('entityId', entityId);
    form.append('file', file);
    return this.http.post<AttachmentResponse>(this.base, form);
  }

  getDownloadUrl(id: string): Observable<DownloadUrlResponse> {
    return this.http.get<DownloadUrlResponse>(`${this.base}/${id}/download-url`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
