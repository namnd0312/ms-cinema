import { Routes } from '@angular/router';
import { authGuard } from '../../core/guards/auth.guard';
import { PaymentPageComponent } from './payment-page/payment-page.component';
import { PaymentStatusComponent } from './payment-status/payment-status.component';
import { PaymentHistoryComponent } from './payment-history/payment-history.component';

export const PAYMENT_ROUTES: Routes = [
  { path: 'pay/:bookingId', component: PaymentPageComponent, canActivate: [authGuard] },
  { path: 'status/:bookingId', component: PaymentStatusComponent, canActivate: [authGuard] },
  { path: 'history', component: PaymentHistoryComponent, canActivate: [authGuard] }
];
