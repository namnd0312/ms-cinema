import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

/**
 * Handles OAuth2 redirect callback from backend.
 * Extracts token + refreshToken from URL query params,
 * stores them via AuthService, clears URL, and redirects to app.
 */
@Component({
  selector: 'app-oauth2-callback',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="callback-container">
      <mat-spinner diameter="40"></mat-spinner>
      <p>{{ message }}</p>
    </div>
  `,
  styles: [`
    .callback-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 80vh;
      gap: 16px;
    }
  `]
})
export class OAuth2CallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  message = 'Signing in...';

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    const refreshToken = this.route.snapshot.queryParamMap.get('refreshToken');
    const error = this.route.snapshot.queryParamMap.get('error');

    // Clear tokens from URL immediately to prevent leakage via browser history
    window.history.replaceState({}, document.title, window.location.pathname);

    if (error) {
      this.message = error === 'no_email'
        ? 'Google did not provide an email address. Please try again.'
        : 'Login failed. Please try again.';
      setTimeout(() => this.router.navigate(['/auth/login']), 2000);
      return;
    }

    if (!token || !refreshToken) {
      this.message = 'Login failed. Missing authentication tokens.';
      setTimeout(() => this.router.navigate(['/auth/login']), 2000);
      return;
    }

    this.authService.handleOAuth2Callback(token, refreshToken).subscribe({
      next: () => this.router.navigate(['/movies']),
      error: () => {
        this.message = 'Login failed. Please try again.';
        setTimeout(() => this.router.navigate(['/auth/login']), 2000);
      }
    });
  }
}
