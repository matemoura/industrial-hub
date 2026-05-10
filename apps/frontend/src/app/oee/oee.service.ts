import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WorkerOeeDto {
  workerId: number;
  workerName: string;
  date: string;
  productiveHours: number | null;
  indirectHours: number | null;
  shiftDuration: number | null;
  availability: number | null;
}

@Injectable({ providedIn: 'root' })
export class OeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/oee';

  getDashboard(startDate: string, endDate: string, workerId?: number): Observable<WorkerOeeDto[]> {
    let params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    if (workerId != null) {
      params = params.set('workerId', workerId.toString());
    }
    return this.http.get<WorkerOeeDto[]>(`${this.baseUrl}/dashboard`, { params });
  }
}
