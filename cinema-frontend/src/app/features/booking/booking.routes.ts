import { Routes } from '@angular/router';
import { authGuard } from '../../core/guards/auth.guard';
import { SeatSelectionComponent } from './seat-selection/seat-selection.component';
import { BookingHistoryComponent } from './booking-history/booking-history.component';
import { BookingDetailComponent } from './booking-detail/booking-detail.component';

export const BOOKING_ROUTES: Routes = [
  { path: 'select/:showtimeId', component: SeatSelectionComponent, canActivate: [authGuard] },
  { path: 'history', component: BookingHistoryComponent, canActivate: [authGuard] },
  { path: ':id', component: BookingDetailComponent, canActivate: [authGuard] }
];
