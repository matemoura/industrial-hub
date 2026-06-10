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

export type AppModule =
  | 'OEE'
  | 'QMS'
  | 'MAINTENANCE'
  | 'PRODUCTION'
  | 'TRAINING'
  | 'CHANGES'
  | 'MANAGEMENT_REVIEW';

export interface UserModulePermissionResponse {
  module: AppModule;
  canView: boolean;
  canCreate: boolean;
  canEdit: boolean;
  canDelete: boolean;
}

export const MODULE_LABELS: Record<AppModule, string> = {
  OEE: 'OEE',
  QMS: 'Qualidade',
  MAINTENANCE: 'Manutenção',
  PRODUCTION: 'Produção',
  TRAINING: 'Treinamentos',
  CHANGES: 'Mudanças',
  MANAGEMENT_REVIEW: 'Análise Crítica',
};

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

  getUserPermissions(userId: string): Observable<UserModulePermissionResponse[]> {
    return this.http.get<UserModulePermissionResponse[]>(`${BASE}/${userId}/permissions`);
  }

  updateUserPermissions(
    userId: string,
    permissions: UserModulePermissionResponse[],
  ): Observable<UserModulePermissionResponse[]> {
    return this.http.put<UserModulePermissionResponse[]>(`${BASE}/${userId}/permissions`, {
      permissions,
    });
  }
}
