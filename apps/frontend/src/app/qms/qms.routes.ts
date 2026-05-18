import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';

export const QMS_ROUTES: Routes = [
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
