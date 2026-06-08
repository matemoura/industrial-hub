import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';

export const CHANGES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./change-request-list/change-request-list.component').then(
        (m) => m.ChangeRequestListComponent,
      ),
  },
  {
    path: ':id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./change-request-detail/change-request-detail.component').then(
        (m) => m.ChangeRequestDetailComponent,
      ),
  },
];
