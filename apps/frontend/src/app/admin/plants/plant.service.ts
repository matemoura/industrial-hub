import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PlantResponse {
  id: string;
  code: string;
  name: string;
  address: string | null;
  timezone: string;
  isDefault: boolean;
  active: boolean;
}

export interface CreatePlantRequest {
  code: string;
  name: string;
  address?: string | null;
  timezone?: string;
}

export interface UpdatePlantRequest {
  name: string;
  address?: string | null;
  timezone?: string;
}

const PLANTS_BASE = '/api/v1/admin/plants';
const USERS_BASE = '/api/v1/admin/users';

@Injectable({ providedIn: 'root' })
export class PlantService {
  private readonly http = inject(HttpClient);

  listPlants(): Observable<PlantResponse[]> {
    return this.http.get<PlantResponse[]>(PLANTS_BASE);
  }

  createPlant(req: CreatePlantRequest): Observable<PlantResponse> {
    return this.http.post<PlantResponse>(PLANTS_BASE, req);
  }

  updatePlant(id: string, req: UpdatePlantRequest): Observable<PlantResponse> {
    return this.http.put<PlantResponse>(`${PLANTS_BASE}/${id}`, req);
  }

  deactivatePlant(id: string): Observable<void> {
    return this.http.put<void>(`${PLANTS_BASE}/${id}/deactivate`, {});
  }

  getUserPlants(userId: string): Observable<PlantResponse[]> {
    return this.http.get<PlantResponse[]>(`${USERS_BASE}/${userId}/plants`);
  }

  updateUserPlants(userId: string, plantIds: string[]): Observable<void> {
    return this.http.put<void>(`${USERS_BASE}/${userId}/plants`, { plantIds });
  }

  /** Alias para updateUserPlants — usado pelo detalhe de usuário (AC#18) */
  assignUserPlants(userId: string, plantIds: string[]): Observable<void> {
    return this.updateUserPlants(userId, plantIds);
  }
}
