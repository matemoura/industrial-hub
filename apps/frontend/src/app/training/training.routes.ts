import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';
import { mustChangePasswordGuard } from '../auth/must-change-password.guard';

export const TRAINING_ROUTES: Routes = [
  {
    path: 'courses',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./training-course-list/training-course-list.component').then(
        (m) => m.TrainingCourseListComponent,
      ),
  },
  {
    path: 'courses/new',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./training-course-form/training-course-form.component').then(
        (m) => m.TrainingCourseFormComponent,
      ),
  },
  {
    path: 'courses/:id/edit',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./training-course-form/training-course-form.component').then(
        (m) => m.TrainingCourseFormComponent,
      ),
  },
  {
    path: 'records',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./training-record-list/training-record-list.component').then(
        (m) => m.TrainingRecordListComponent,
      ),
  },
  {
    path: 'records/me',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./my-training-records/my-training-records.component').then(
        (m) => m.MyTrainingRecordsComponent,
      ),
  },
  {
    path: 'competency-matrix',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./competency-matrix/competency-matrix.component').then(
        (m) => m.CompetencyMatrixComponent,
      ),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./training-dashboard/training-dashboard.component').then(
        (m) => m.TrainingDashboardComponent,
      ),
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
