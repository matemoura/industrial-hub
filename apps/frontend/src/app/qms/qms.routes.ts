import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';

export const QMS_ROUTES: Routes = [
  // ── Reclamações de Clientes (Sprint 45 — US-134) ── estáticas antes de /:id
  {
    path: 'complaints',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./complaints/complaint-list/complaint-list.component').then((m) => m.ComplaintListComponent),
  },
  {
    path: 'complaints/dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./complaints/complaint-dashboard/complaint-dashboard.component').then((m) => m.ComplaintDashboardComponent),
  },
  {
    path: 'complaints/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./complaints/complaint-detail/complaint-detail.component').then((m) => m.ComplaintDetailComponent),
  },
  // ── CAPAS — lista cross-NC de ações corretivas/preventivas
  {
    path: 'capas',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./capa-list/capa-list.component').then((m) => m.CapaListComponent),
  },
  // ── US-116: CAPA Aging Dashboard — rota estática ANTES de qualquer :id
  {
    path: 'capas/aging',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./aging-dashboard/aging-dashboard.component').then((m) => m.AgingDashboardComponent),
  },
  // ── GED — Gestão de Documentos ── rotas estáticas ANTES de /:id
  {
    path: 'ged',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./ged-list/ged-list.component').then((m) => m.GedListComponent),
  },
  {
    path: 'ged/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./ged-detail/ged-detail.component').then((m) => m.GedDetailComponent),
  },
  {
    path: 'non-conformances',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./nc-list/nc-list.component').then((m) => m.NcListComponent),
  },
  {
    path: 'non-conformances/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./nc-form/nc-form.component').then((m) => m.NcFormComponent),
  },
  {
    path: 'non-conformances/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./nc-detail/nc-detail.component').then((m) => m.NcDetailComponent),
  },
  // ── US-117: Relatório Executivo de Qualidade
  {
    path: 'reports/quality',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./quality-report/quality-report.component').then((m) => m.QualityReportComponent),
  },
  // ── Gestão de Risco / FMEA (Sprint 43 — US-129) ── estáticas antes de /:id
  {
    path: 'risks',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./risk/risk-list/risk-list.component').then((m) => m.RiskListComponent),
  },
  {
    path: 'risks/matrix',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./risk/risk-matrix/risk-matrix.component').then((m) => m.RiskMatrixComponent),
  },
  {
    path: 'risks/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./risk/risk-detail/risk-detail.component').then((m) => m.RiskDetailComponent),
  },
  // ── Auditorias Internas (Sprint 42 — US-126) ── estáticas antes de /:id
  {
    path: 'audits',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./audit/audit-list/audit-list.component').then((m) => m.AuditListComponent),
  },
  {
    path: 'audits/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./audit/audit-detail/audit-detail.component').then((m) => m.AuditDetailComponent),
  },
  // ── Suppliers ── rotas estáticas ANTES de /:id para evitar conflito
  {
    path: 'suppliers',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./supplier-list/supplier-list.component').then((m) => m.SupplierListComponent),
  },
  {
    path: 'suppliers/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./supplier-form/supplier-form.component').then((m) => m.SupplierFormComponent),
  },
  {
    path: 'suppliers/ranking',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./supplier-ranking/supplier-ranking.component').then((m) => m.SupplierRankingComponent),
  },
  {
    path: 'suppliers/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./supplier-detail/supplier-detail.component').then((m) => m.SupplierDetailComponent),
  },
  {
    path: 'suppliers/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./supplier-form/supplier-form.component').then((m) => m.SupplierFormComponent),
  },
];
