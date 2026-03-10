import { Component, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <mat-toolbar color="primary" class="toolbar">
      <button mat-icon-button (click)="menuToggle.emit()">
        <mat-icon>menu</mat-icon>
      </button>
      <a routerLink="/movies" class="brand">Cinema</a>

      <span class="spacer"></span>

      <nav class="nav-links">
        <a mat-button routerLink="/movies" routerLinkActive="active">Movies</a>
        @if (auth.isAuthenticated()) {
          <a mat-button routerLink="/booking/history" routerLinkActive="active">My Bookings</a>
          <a mat-button routerLink="/payment/history" routerLinkActive="active">Payments</a>
          @if (auth.hasRole('ROLE_ADMIN')) {
            <a mat-button routerLink="/admin" routerLinkActive="active">Admin</a>
          }
        }
      </nav>

      @if (auth.isAuthenticated()) {
        <button mat-icon-button [matMenuTriggerFor]="userMenu">
          <mat-icon>account_circle</mat-icon>
        </button>
        <mat-menu #userMenu="matMenu">
          <div class="user-info" mat-menu-item disabled>
            {{ auth.currentUser()?.fullName || auth.currentUser()?.email }}
          </div>
          <a mat-menu-item routerLink="/profile">
            <mat-icon>person</mat-icon> Profile
          </a>
          <button mat-menu-item (click)="auth.logout()">
            <mat-icon>logout</mat-icon> Logout
          </button>
        </mat-menu>
      } @else {
        <a mat-button routerLink="/auth/login">Login</a>
      }
    </mat-toolbar>
  `,
  styles: [`
    .toolbar { position: sticky; top: 0; z-index: 1000; }
    .brand { text-decoration: none; color: inherit; font-size: 1.3rem; font-weight: 600; margin-left: 8px; }
    .spacer { flex: 1; }
    .nav-links { display: flex; gap: 4px; }
    .nav-links .active { background: rgba(255,255,255,0.1); }
    .user-info { font-weight: 500; opacity: 0.7; }
    @media (max-width: 768px) {
      .nav-links { display: none; }
    }
  `]
})
export class ToolbarComponent {
  auth = inject(AuthService);
  menuToggle = output<void>();
}
