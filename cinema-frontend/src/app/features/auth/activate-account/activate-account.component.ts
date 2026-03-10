import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-activate-account',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatProgressSpinnerModule, MatIconModule, RouterLink],
  template: `
    <div class="auth-container">
      <mat-card class="auth-card">
        <mat-card-content class="center">
          @if (loading()) {
            <mat-spinner diameter="40"></mat-spinner>
            <p>Activating your account...</p>
          } @else if (success()) {
            <mat-icon class="success-icon">check_circle</mat-icon>
            <h2>Account Activated!</h2>
            <p>Your account has been activated successfully.</p>
            <a mat-raised-button routerLink="/auth/login" color="primary">Go to Login</a>
          } @else {
            <mat-icon class="error-icon">error</mat-icon>
            <h2>Activation Failed</h2>
            <p>{{ errorMessage() }}</p>
            <a mat-raised-button routerLink="/auth/login" color="primary">Go to Login</a>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-container { display: flex; justify-content: center; align-items: center; min-height: 80vh; padding: 16px; }
    .auth-card { max-width: 400px; width: 100%; }
    .center { text-align: center; padding: 32px 16px; }
    .success-icon { font-size: 64px; height: 64px; width: 64px; color: #4caf50; }
    .error-icon { font-size: 64px; height: 64px; width: 64px; color: #f44336; }
  `]
})
export class ActivateAccountComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);
  loading = signal(true);
  success = signal(false);
  errorMessage = signal('Invalid or expired activation link.');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParams['token'];
    if (!token) {
      this.loading.set(false);
      this.errorMessage.set('No activation token provided.');
      return;
    }
    this.authService.activateAccount(token).subscribe({
      next: () => { this.success.set(true); this.loading.set(false); },
      error: (err) => {
        this.errorMessage.set(err.error || 'Invalid or expired activation link.');
        this.loading.set(false);
      }
    });
  }
}
