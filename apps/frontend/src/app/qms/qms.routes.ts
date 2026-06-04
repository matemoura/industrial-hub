import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';

export const QMS_ROUTES: Routes = [
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
