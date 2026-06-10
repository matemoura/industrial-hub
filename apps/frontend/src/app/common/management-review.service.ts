import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface NcSummary {
  totalReported: number;
  criticalOpen: number;
  avgResolutionDays: number | null;
  byStatus: Record<string, number>;
  bySeverity: Record<string, number>;
}

export interface CapaSummary {
  totalOpen: number;
  overdueCount: number;
  effectivenessRate: number | null;
}

export interface ComplaintSummary {
  totalReceived: number;
  reportedToAnvisa: number;
  avgResolutionDays: number | null;
}

export interface AuditSummary {
  completed: number;
  plannedNotDone: number;
  overdueAudits: number;
  nonConformingFindings: number;
  conformityRate: number | null;
}

export interface CalibrationSummary {
  overdueSchedules: number;
  outOfToleranceCount: number;
  complianceRate: number | null;
}

export interface TrainingSummary {
  fullyCompliant: number;
  partiallyCompliant: number;
  nonCompliant: number;
  expiringIn30Days: number;
}

export interface RiskSummary {
  totalRisks: number;
  criticalOpen: number;
  mitigatedInPeriod: number;
  avgRpn: number | null;
}

export interface ChangeSummary {
  submitted: number;
  approved: number;
  rejected: number;
  implemented: number;
  pending: number;
}

export interface KpiSnapshot {
  oee30Days: number | null;
  openNcs: number;
  openWorkOrders: number;
}

export interface ManagementReviewData {
  ncSummary: NcSummary;
  capaSummary: CapaSummary;
  complaintSummary: ComplaintSummary;
  auditSummary: AuditSummary;
  calibrationSummary: CalibrationSummary;
  trainingSummary: TrainingSummary;
  riskSummary: RiskSummary;
  changeSummary: ChangeSummary;
  kpiSummary: KpiSnapshot;
}

@Injectable({ providedIn: 'root' })
export class ManagementReviewService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/management-review';

  getIndicators(from: string, to: string): Observable<ManagementReviewData> {
    return this.http.get<ManagementReviewData>(`${this.base}/indicators`, {
      params: { from, to },
    });
  }

  exportPdf(from: string, to: string): Observable<Blob> {
    return this.http.get(`${this.base}/indicators/export`, {
      params: { from, to },
      responseType: 'blob',
    });
  }
}
