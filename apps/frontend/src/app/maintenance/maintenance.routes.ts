import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';

export const MAINTENANCE_ROUTES: Routes = [
  {
    path: 'equipment',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./equipment-list/equipment-list.component').then((m) => m.EquipmentListComponent),
  },
  {
    path: 'equipment/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./equipment-form/equipment-form.component').then((m) => m.EquipmentFormComponent),
  },
  {
    path: 'equipment/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./equipment-form/equipment-form.component').then((m) => m.EquipmentFormComponent),
  },
  {
    path: 'equipment/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./equipment-detail/equipment-detail.component').then((m) => m.EquipmentDetailComponent),
  },
  {
    path: 'work-orders',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./work-order-list/work-order-list.component').then((m) => m.WorkOrderListComponent),
  },
  {
    path: 'schedules',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./schedule-list/schedule-list.component').then((m) => m.ScheduleListComponent),
  },
  {
    path: 'schedules/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./schedule-form/schedule-form.component').then((m) => m.ScheduleFormComponent),
  },
  {
    path: 'schedules/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./schedule-form/schedule-form.component').then((m) => m.ScheduleFormComponent),
  },
  {
    path: 'schedules/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./schedule-list/schedule-list.component').then((m) => m.ScheduleListComponent),
  },
  {
    path: 'calendar',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./maintenance-calendar/maintenance-calendar.component').then(
        (m) => m.MaintenanceCalendarComponent,
      ),
  },
];
