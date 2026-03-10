import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressSpinnerModule, RouterLink],
  template: `
    <div class="auth-container">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Create Account</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (registered()) {
            <div class="success-message">
              <p>Registration successful! Please check your email to activate your account.</p>
              <a mat-button routerLink="/auth/login" color="primary">Go to Login</a>
            </div>
          } @else {
            <form [formGroup]="form" (ngSubmit)="onSubmit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Username</mat-label>
                <input matInput formControlName="username">
                @if (form.controls.username.hasError('required') && form.controls.username.touched) {
                  <mat-error>Username is required</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Full Name</mat-label>
                <input matInput formControlName="fullName">
                @if (form.controls.fullName.hasError('required') && form.controls.fullName.touched) {
                  <mat-error>Full name is required</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email">
                @if (form.controls.email.hasError('email')) {
                  <mat-error>Invalid email format</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Password</mat-label>
                <input matInput formControlName="password" type="password">
                @if (form.controls.password.hasError('minlength')) {
                  <mat-error>Password must be at least 6 characters</mat-error>
                }
              </mat-form-field>

              <button mat-raised-button color="primary" type="submit" class="full-width"
                [disabled]="form.invalid || loading()">
                @if (loading()) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  Register
                }
              </button>
            </form>
          }
        </mat-card-content>
        @if (!registered()) {
          <mat-card-actions align="end">
            <a mat-button routerLink="/auth/login">Already have an account?</a>
          </mat-card-actions>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-container { display: flex; justify-content: center; align-items: center; min-height: 80vh; padding: 16px; }
    .auth-card { max-width: 450px; width: 100%; }
    .full-width { width: 100%; }
    form { display: flex; flex-direction: column; gap: 8px; }
    .success-message { text-align: center; padding: 24px 0; }
  `]
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  loading = signal(false);
  registered = signal(false);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.authService.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.registered.set(true);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
