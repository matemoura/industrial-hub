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
];
