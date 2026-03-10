import { Routes } from '@angular/router';
import { adminGuard } from '../../core/guards/admin.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    canActivate: [adminGuard],
    children: [
      { path: '', redirectTo: 'theaters', pathMatch: 'full' },
      {
        path: 'theaters',
        loadComponent: () => import('./theater-management/theater-management.component').then(m => m.TheaterManagementComponent)
      },
      {
        path: 'showtimes',
        loadComponent: () => import('./showtime-management/showtime-management.component').then(m => m.ShowtimeManagementComponent)
      }
    ]
  }
];
