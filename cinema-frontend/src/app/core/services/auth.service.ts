import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { User, LoginRequest, RegisterRequest, JwtResponse, TokenRefreshResponse } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  currentUser = signal<User | null>(null);
  isAuthenticated = computed(() => !!this.currentUser());

  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_KEY = 'refresh_token';
  private readonly USER_KEY = 'auth_user';

  constructor() {
    this.loadStoredUser();
  }

  private loadStoredUser(): void {
    const userJson = localStorage.getItem(this.USER_KEY);
    const token = this.getToken();
    if (userJson && token) {
      this.currentUser.set(JSON.parse(userJson));
    }
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_KEY);
  }

  private setTokens(token: string, refreshToken: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    localStorage.setItem(this.REFRESH_KEY, refreshToken);
  }

  private clearTokens(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  login(credentials: LoginRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>('/api/auth/login', credentials).pipe(
      tap(response => {
        this.setTokens(response.token, response.refreshToken);
        const user: User = {
          id: response.id,
          username: response.username,
          email: response.email,
          fullName: response.name,
          roles: (response.roles as any[]).map(r => typeof r === 'string' ? r : r.authority)
        };
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.currentUser.set(user);
      })
    );
  }

  register(data: RegisterRequest): Observable<any> {
    return this.http.post('/api/auth/register', data, { responseType: 'text' });
  }

  logout(): void {
    const token = this.getToken();
    if (token) {
      this.http.post('/api/auth/logout', {}).subscribe({ error: () => {} });
    }
    this.clearTokens();
    this.currentUser.set(null);
    this.router.navigate(['/auth/login']);
  }

  refreshToken(): Observable<TokenRefreshResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<TokenRefreshResponse>('/api/auth/refresh-token', { refreshToken }).pipe(
      tap(response => {
        this.setTokens(response.accessToken, response.refreshToken);
      })
    );
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post('/api/auth/forgot-password', { email }, { responseType: 'text' });
  }

  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post('/api/auth/reset-password', { token, newPassword }, { responseType: 'text' });
  }

  activateAccount(token: string): Observable<any> {
    return this.http.get('/api/auth/activate', { params: { token }, responseType: 'text' });
  }

  setupPassword(token: string, password: string, confirmPassword: string): Observable<any> {
    return this.http.post('/api/auth/activate-with-password', {
      token, password, confirmPassword
    }, { responseType: 'text' });
  }

  changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Observable<any> {
    return this.http.post('/api/auth/change-password', {
      currentPassword, newPassword, confirmPassword
    }, { responseType: 'text' });
  }

  resendActivation(email: string): Observable<any> {
    return this.http.post('/api/auth/resend-activation', { email }, { responseType: 'text' });
  }

  /**
   * Handles OAuth2 callback by storing tokens and fetching user profile.
   * Called from OAuth2CallbackComponent after redirect from backend.
   */
  handleOAuth2Callback(token: string, refreshToken: string): Observable<any> {
    // Store tokens first so the auth interceptor can attach Bearer header
    this.setTokens(token, refreshToken);
    return this.http.get<any>('/api/users/me').pipe(
      tap({
        next: (userInfo) => {
          const user: User = {
            id: userInfo.id,
            username: userInfo.username,
            email: userInfo.email,
            fullName: userInfo.fullName,
            roles: userInfo.roles
          };
          localStorage.setItem(this.USER_KEY, JSON.stringify(user));
          this.currentUser.set(user);
        },
        error: () => {
          // Clear tokens if profile fetch fails
          this.clearTokens();
        }
      })
    );
  }

  hasRole(role: string): boolean {
    return this.currentUser()?.roles?.includes(role) ?? false;
  }
}
