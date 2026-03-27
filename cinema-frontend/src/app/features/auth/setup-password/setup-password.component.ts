import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-setup-password',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressSpinnerModule, MatIconModule, RouterLink],
  template: `
    <div class="auth-container">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Set Up Your Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (noToken()) {
            <div class="center">
              <mat-icon class="error-icon">error</mat-icon>
              <h2>Invalid Link</h2>
              <p>No activation token provided.</p>
              <a mat-raised-button routerLink="/auth/login" color="primary">Go to Login</a>
            </div>
          } @else if (success()) {
            <div class="center">
              <mat-icon class="success-icon">check_circle</mat-icon>
              <h2>Account Activated!</h2>
              <p>Your password has been set and your account is now active.</p>
              <a mat-raised-button routerLink="/auth/login" color="primary">Go to Login</a>
            </div>
          } @else {
            <form [formGroup]="form" (ngSubmit)="onSubmit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Password</mat-label>
                <input matInput formControlName="password" type="password" autocomplete="new-password">
                @if (form.controls.password.hasError('minlength')) {
                  <mat-error>Password must be at least 6 characters</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Confirm Password</mat-label>
                <input matInput formControlName="confirmPassword" type="password" autocomplete="new-password">
                @if (form.hasError('mismatch') && form.controls.confirmPassword.touched) {
                  <mat-error>Passwords do not match</mat-error>
                }
              </mat-form-field>
              @if (errorMessage()) {
                <p class="error-text">{{ errorMessage() }}</p>
              }
              <button mat-raised-button color="primary" type="submit" class="full-width"
                [disabled]="form.invalid || loading()">
                @if (loading()) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  Set Password & Activate
                }
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
    .center { text-align: center; padding: 32px 16px; }
    .success-icon { font-size: 64px; height: 64px; width: 64px; color: #4caf50; }
    .error-icon { font-size: 64px; height: 64px; width: 64px; color: #f44336; }
    .error-text { color: #f44336; text-align: center; margin: 0; }
  `]
})
export class SetupPasswordComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);

  loading = signal(false);
  success = signal(false);
  noToken = signal(false);
  errorMessage = signal('');

  private token = '';

  form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required]]
  }, { validators: [this.passwordMatch] });

  passwordMatch(group: any) {
    const pass = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { mismatch: true };
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParams['token'] || '';
    if (!this.token) {
      this.noToken.set(true);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMessage.set('');
    this.authService.setupPassword(
      this.token,
      this.form.value.password!,
      this.form.value.confirmPassword!
    ).subscribe({
      next: () => { this.success.set(true); this.loading.set(false); },
      error: (err) => {
        this.errorMessage.set(err.error || 'Failed to activate account. The link may be invalid or expired.');
        this.loading.set(false);
      }
    });
  }
}
