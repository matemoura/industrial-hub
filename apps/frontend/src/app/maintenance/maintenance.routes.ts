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
];
