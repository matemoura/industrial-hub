import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface Notification {
  id: string;
  username: string | null;
  title: string;
  body: string;
  severity: NotificationSeverity;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationPage {
  content: Notification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UnreadCountResponse {
  count: number;
}

const BASE = '/api/v1/notifications';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);

  getNotifications(page = 0): Observable<NotificationPage> {
    const params = new HttpParams().set('page', page.toString());
    return this.http.get<NotificationPage>(BASE, { params });
  }

  getUnreadCount(): Observable<UnreadCountResponse> {
    return this.http.get<UnreadCountResponse>(`${BASE}/unread-count`);
  }

  markRead(id: string): Observable<void> {
    return this.http.put<void>(`${BASE}/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.put<void>(`${BASE}/read-all`, {});
  }
}
