import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { ForgotPasswordComponent } from './forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './reset-password/reset-password.component';
import { ActivateAccountComponent } from './activate-account/activate-account.component';
import { SetupPasswordComponent } from './setup-password/setup-password.component';
import { OAuth2CallbackComponent } from './oauth2-callback/oauth2-callback.component';

export const AUTH_ROUTES: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'activate', component: ActivateAccountComponent },
  { path: 'setup-password', component: SetupPasswordComponent },
  { path: 'oauth2/callback', component: OAuth2CallbackComponent },
  { path: '', redirectTo: 'login', pathMatch: 'full' }
];
