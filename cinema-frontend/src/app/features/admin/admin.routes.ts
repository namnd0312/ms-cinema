import { Routes } from '@angular/router';
import { adminGuard } from '../../core/guards/admin.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    canActivate: [adminGuard],
    loadComponent: () => import('./admin-nav/admin-nav.component').then(m => m.AdminNavComponent),
    children: [
      { path: '', redirectTo: 'movies', pathMatch: 'full' },
      {
        path: 'movies',
        loadComponent: () => import('./movie-management/movie-management.component')
          .then(m => m.MovieManagementComponent)
      },
      {
        path: 'theaters',
        loadComponent: () => import('./theater-management/theater-management.component')
          .then(m => m.TheaterManagementComponent)
      },
      {
        path: 'showtimes',
        loadComponent: () => import('./showtime-management/showtime-management.component')
          .then(m => m.ShowtimeManagementComponent)
      },
      {
        path: 'payments',
        loadComponent: () => import('./payment-management/payment-management.component')
          .then(m => m.PaymentManagementComponent)
      },
      {
        path: 'reconciliation',
        loadComponent: () => import('./reconciliation/reconciliation-dashboard.component')
          .then(m => m.ReconciliationDashboardComponent)
      },
      {
        path: 'reconciliation/:runId',
        loadComponent: () => import('./reconciliation/reconciliation-detail.component')
          .then(m => m.ReconciliationDetailComponent)
      }
    ]
  }
];
