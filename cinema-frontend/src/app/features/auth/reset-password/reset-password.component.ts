import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressSpinnerModule, RouterLink],
  template: `
    <div class="auth-container">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Reset Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (success()) {
            <p class="success-message">Password has been reset successfully.</p>
            <a mat-raised-button routerLink="/auth/login" color="primary" class="full-width">Go to Login</a>
          } @else {
            <form [formGroup]="form" (ngSubmit)="onSubmit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>New Password</mat-label>
                <input matInput formControlName="newPassword" type="password">
              </mat-form-field>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Confirm Password</mat-label>
                <input matInput formControlName="confirmPassword" type="password">
                @if (form.hasError('mismatch')) {
                  <mat-error>Passwords do not match</mat-error>
                }
              </mat-form-field>
              <button mat-raised-button color="primary" type="submit" class="full-width"
                [disabled]="form.invalid || loading()">
                @if (loading()) { <mat-spinner diameter="20"></mat-spinner> } @else { Reset Password }
              </button>
            </form>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-container { display: flex; justify-content: center; align-items: center; min-height: 80vh; padding: 16px; }
    .auth-card { max-width: 400px; width: 100%; }
    .full-width { width: 100%; }
    form { display: flex; flex-direction: column; gap: 8px; }
    .success-message { text-align: center; padding: 16px 0; }
  `]
})
export class ResetPasswordComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  loading = signal(false);
  success = signal(false);

  form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required]]
  }, { validators: [this.passwordMatch] });

  passwordMatch(group: any) {
    const pass = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { mismatch: true };
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    const token = this.route.snapshot.queryParams['token'];
    this.authService.resetPassword(token, this.form.value.newPassword!).subscribe({
      next: () => { this.success.set(true); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
