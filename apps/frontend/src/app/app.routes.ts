import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./oee/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
