import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { adminGuard } from './auth/admin.guard';
import { mustChangePasswordGuard } from './auth/must-change-password.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./auth/change-password/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
  },
  {
    path: 'admin/users',
    canActivate: [adminGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./admin/user-list/user-list.component').then((m) => m.UserListComponent),
  },
  {
    path: 'admin/alert-thresholds',
    canActivate: [adminGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./admin/alert-thresholds/alert-thresholds.component').then(
        (m) => m.AlertThresholdsComponent,
      ),
  },
  {
    path: 'admin/shifts',
    canActivate: [adminGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./admin/shifts/shift-list.component').then((m) => m.ShiftListComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./kpi/kpi-dashboard/kpi-dashboard.component').then((m) => m.KpiDashboardComponent),
  },
  {
    path: 'oee/efficiency',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./oee/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'oee/planned-downtimes',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./oee/planned-downtime-calendar/planned-downtime-calendar.component').then(
        (m) => m.PlannedDowntimeCalendarComponent,
      ),
  },
  {
    path: 'indirect-activities',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./oee/indirect-activities/indirect-activities.component').then(
        (m) => m.IndirectActivitiesComponent,
      ),
  },
  {
    path: 'summary',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./oee/summary/summary.component').then((m) => m.SummaryComponent),
  },
  {
    path: 'processes',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./oee/processes/processes.component').then((m) => m.ProcessesComponent),
  },
  {
    path: 'qms',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadChildren: () => import('./qms/qms.routes').then((m) => m.QMS_ROUTES),
  },
  {
    path: 'maintenance',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadChildren: () => import('./maintenance/maintenance.routes').then((m) => m.MAINTENANCE_ROUTES),
  },
  {
    path: 'analytics/oee',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./analytics/oee-analytics/oee-analytics.component').then(
        (m) => m.OeeAnalyticsComponent,
      ),
  },
  {
    path: 'analytics/qms',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./analytics/qms-analytics/qms-analytics.component').then(
        (m) => m.QmsAnalyticsComponent,
      ),
  },
  {
    path: 'analytics/maintenance',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./analytics/maintenance-analytics/maintenance-analytics.component').then(
        (m) => m.MaintenanceAnalyticsComponent,
      ),
  },
  {
    path: 'notifications',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./notifications/notifications-page/notifications-page.component').then(
        (m) => m.NotificationsPageComponent,
      ),
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
