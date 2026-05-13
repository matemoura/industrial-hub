import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./oee/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'indirect-activities',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./oee/indirect-activities/indirect-activities.component').then(
        (m) => m.IndirectActivitiesComponent,
      ),
  },
  {
    path: 'summary',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./oee/summary/summary.component').then((m) => m.SummaryComponent),
  },
  {
    path: 'processes',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./oee/processes/processes.component').then((m) => m.ProcessesComponent),
  },
  {
    path: 'qms',
    canActivate: [authGuard],
    loadChildren: () => import('./qms/qms.routes').then((m) => m.QMS_ROUTES),
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
