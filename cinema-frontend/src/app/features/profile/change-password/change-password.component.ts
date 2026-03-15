import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, ReactiveFormsModule
  ],
  template: `
    <div class="container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Change Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current Password</mat-label>
              <input matInput [type]="hideCurrentPw() ? 'password' : 'text'"
                     formControlName="currentPassword">
              <button mat-icon-button matSuffix type="button"
                      (click)="hideCurrentPw.set(!hideCurrentPw())">
                <mat-icon>{{hideCurrentPw() ? 'visibility_off' : 'visibility'}}</mat-icon>
              </button>
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Password</mat-label>
              <input matInput [type]="hideNewPw() ? 'password' : 'text'"
                     formControlName="newPassword">
              <button mat-icon-button matSuffix type="button"
                      (click)="hideNewPw.set(!hideNewPw())">
                <mat-icon>{{hideNewPw() ? 'visibility_off' : 'visibility'}}</mat-icon>
              </button>
              <mat-error>Min 6 characters</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm Password</mat-label>
              <input matInput [type]="hideConfirmPw() ? 'password' : 'text'"
                     formControlName="confirmPassword">
              <button mat-icon-button matSuffix type="button"
                      (click)="hideConfirmPw.set(!hideConfirmPw())">
                <mat-icon>{{hideConfirmPw() ? 'visibility_off' : 'visibility'}}</mat-icon>
              </button>
              @if (form.hasError('passwordMismatch')) {
                <mat-error>Passwords do not match</mat-error>
              }
            </mat-form-field>

            <button mat-raised-button color="primary" type="submit"
                    [disabled]="form.invalid || submitting()">
              {{ submitting() ? 'Changing...' : 'Change Password' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 500px; margin: 0 auto; }
    .full-width { width: 100%; margin-bottom: 8px; }
    button[type="submit"] { width: 100%; }
  `]
})
export class ChangePasswordComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  hideCurrentPw = signal(true);
  hideNewPw = signal(true);
  hideConfirmPw = signal(true);
  submitting = signal(false);

  form = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatchValidator });

  private passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const newPw = control.get('newPassword')?.value;
    const confirmPw = control.get('confirmPassword')?.value;
    return newPw && confirmPw && newPw !== confirmPw ? { passwordMismatch: true } : null;
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting.set(true);

    const { currentPassword, newPassword, confirmPassword } = this.form.getRawValue();
    this.authService.changePassword(currentPassword!, newPassword!, confirmPassword!).subscribe({
      next: () => {
        this.submitting.set(false);
        this.snackBar.open('Password changed successfully', 'Close', { duration: 3000 });
        this.router.navigate(['/profile']);
      },
      error: () => this.submitting.set(false)
    });
  }
}
