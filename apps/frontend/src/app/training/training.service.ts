import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TrainingCategory = 'GMP' | 'QUALITY' | 'SAFETY' | 'REGULATORY' | 'TECHNICAL' | 'OTHER';
export type EffectivenessResult = 'EFFECTIVE' | 'PARTIALLY_EFFECTIVE' | 'NOT_EFFECTIVE';
export type CompetencyStatus = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'MISSING';

export interface TrainingCourse {
  id: string;
  code: string;
  title: string;
  description?: string;
  category: TrainingCategory;
  durationHours: number;
  validityMonths?: number;
  requiredForRoles: string[];
  active: boolean;
  createdAt: string;
}

export interface TrainingRecord {
  id: string;
  courseId: string;
  courseCode: string;
  courseTitle: string;
  username: string;
  completedAt: string;
  expiresAt?: string;
  instructorName?: string;
  score?: number;
  passed: boolean;
  hasCertificate: boolean;
  recordedBy: string;
  recordedAt: string;
  effectivenessAssessedAt?: string;
  effectivenessAssessedBy?: string;
  effectivenessResult?: EffectivenessResult;
  effectivenessNotes?: string;
}

export interface CompetencyMatrixRow {
  username: string;
  role: string;
  courseId: string;
  courseCode: string;
  courseTitle: string;
  status: CompetencyStatus;
  completedAt?: string;
  expiresAt?: string;
}

export interface TrainingComplianceSummary {
  totalUsers: number;
  totalRequiredCompetencies: number;
  valid: number;
  expiring: number;
  expired: number;
  missing: number;
  compliancePercent: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateCourseRequest {
  code: string;
  title: string;
  description?: string;
  category: TrainingCategory;
  durationHours: number;
  validityMonths?: number;
  requiredForRoles: string[];
}

export interface AssessEffectivenessRequest {
  result: EffectivenessResult;
  notes?: string;
}

const BASE = '/api/v1/training';

@Injectable({ providedIn: 'root' })
export class TrainingService {
  private readonly http = inject(HttpClient);

  // ── Courses ──────────────────────────────────────────────────────────────

  getCourses(page = 0, size = 20): Observable<PageResponse<TrainingCourse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<TrainingCourse>>(`${BASE}/courses`, { params });
  }

  getCourse(id: string): Observable<TrainingCourse> {
    return this.http.get<TrainingCourse>(`${BASE}/courses/${id}`);
  }

  createCourse(req: CreateCourseRequest): Observable<TrainingCourse> {
    return this.http.post<TrainingCourse>(`${BASE}/courses`, req);
  }

  updateCourse(id: string, req: Omit<CreateCourseRequest, 'code'>): Observable<TrainingCourse> {
    return this.http.put<TrainingCourse>(`${BASE}/courses/${id}`, req);
  }

  deactivateCourse(id: string): Observable<void> {
    return this.http.put<void>(`${BASE}/courses/${id}/deactivate`, {});
  }

  // ── Records ──────────────────────────────────────────────────────────────

  getRecords(filters: { username?: string; courseId?: string; passed?: boolean }, page = 0, size = 20): Observable<PageResponse<TrainingRecord>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.username) params = params.set('username', filters.username);
    if (filters.courseId) params = params.set('courseId', filters.courseId);
    if (filters.passed !== undefined) params = params.set('passed', String(filters.passed));
    return this.http.get<PageResponse<TrainingRecord>>(`${BASE}/records`, { params });
  }

  getMyRecords(): Observable<TrainingRecord[]> {
    return this.http.get<TrainingRecord[]>(`${BASE}/records/me`);
  }

  createRecord(formData: FormData): Observable<TrainingRecord> {
    return this.http.post<TrainingRecord>(`${BASE}/records`, formData);
  }

  getCertificateUrl(recordId: string): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(`${BASE}/records/${recordId}/certificate`);
  }

  deleteRecord(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/records/${id}`);
  }

  assessEffectiveness(recordId: string, req: AssessEffectivenessRequest): Observable<TrainingRecord> {
    return this.http.post<TrainingRecord>(`${BASE}/records/${recordId}/effectiveness`, req);
  }

  // ── Analysis ─────────────────────────────────────────────────────────────

  getCompetencyMatrix(): Observable<CompetencyMatrixRow[]> {
    return this.http.get<CompetencyMatrixRow[]>(`${BASE}/competency-matrix`);
  }

  getComplianceSummary(): Observable<TrainingComplianceSummary> {
    return this.http.get<TrainingComplianceSummary>(`${BASE}/compliance-summary`);
  }

  runAlertsNow(): Observable<{ alerted: number }> {
    return this.http.post<{ alerted: number }>(`${BASE}/admin/alerts/run-now`, {});
  }
}
