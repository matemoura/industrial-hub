import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  role: 'OPERATOR' | 'SUPERVISOR' | 'ADMIN';
  active: boolean;
  mustChangePassword: boolean;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  role: 'OPERATOR' | 'SUPERVISOR' | 'ADMIN';
  temporaryPassword: string;
}

export interface UpdateUserRoleRequest {
  role: 'OPERATOR' | 'SUPERVISOR' | 'ADMIN';
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ChangePasswordResponse {
  token: string;
  username: string;
  role: string;
  expiresInMs: number;
  mustChangePassword: boolean;
}

const BASE = '/api/v1/admin/users';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  list(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(BASE);
  }

  create(request: CreateUserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(BASE, request);
  }

  updateRole(id: string, request: UpdateUserRoleRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${BASE}/${id}/role`, request);
  }

  deactivate(id: string): Observable<void> {
    return this.http.put<void>(`${BASE}/${id}/deactivate`, {});
  }

  reactivate(id: string): Observable<void> {
    return this.http.put<void>(`${BASE}/${id}/reactivate`, {});
  }

  changePassword(request: ChangePasswordRequest): Observable<ChangePasswordResponse> {
    return this.http.put<ChangePasswordResponse>('/api/v1/users/me/password', request);
  }

  exportMyData(): Observable<Blob> {
    return this.http.get('/api/v1/users/me/data-export', { responseType: 'blob' });
  }
}
