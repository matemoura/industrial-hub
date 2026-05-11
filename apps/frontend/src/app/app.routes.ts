import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./oee/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'indirect-activities',
    loadComponent: () =>
      import('./oee/indirect-activities/indirect-activities.component').then(
        (m) => m.IndirectActivitiesComponent,
      ),
  },
  {
    path: 'summary',
    loadComponent: () =>
      import('./oee/summary/summary.component').then((m) => m.SummaryComponent),
  },
  {
    path: 'processes',
    loadComponent: () =>
      import('./oee/processes/processes.component').then((m) => m.ProcessesComponent),
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
