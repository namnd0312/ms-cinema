import { Routes } from '@angular/router';
import { authGuard } from '../../core/guards/auth.guard';
import { ProfilePageComponent } from './profile-page/profile-page.component';
import { ChangePasswordComponent } from './change-password/change-password.component';

export const PROFILE_ROUTES: Routes = [
  { path: '', component: ProfilePageComponent, canActivate: [authGuard] },
  { path: 'change-password', component: ChangePasswordComponent, canActivate: [authGuard] }
];
