import { Routes } from '@angular/router';

export const CALIBRATION_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./calibration-list/calibration-list.component').then(
        (m) => m.CalibrationListComponent,
      ),
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./calibration-dashboard/calibration-dashboard.component').then(
        (m) => m.CalibrationDashboardComponent,
      ),
  },
];
