import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { ToolbarComponent } from './shared/components/toolbar/toolbar.component';
import { LoadingBarComponent } from './shared/components/loading-bar/loading-bar.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule,
    MatIconModule, ToolbarComponent, LoadingBarComponent],
  template: `
    <app-loading-bar />
    <mat-sidenav-container class="app-container">
      <mat-sidenav #sidenav [mode]="isMobile ? 'over' : 'side'" [opened]="false" class="sidenav">
        <mat-nav-list>
          <a mat-list-item routerLink="/movies" routerLinkActive="active" (click)="sidenav.close()">
            <mat-icon matListItemIcon>movie</mat-icon> Movies
          </a>
          @if (auth.isAuthenticated()) {
            <a mat-list-item routerLink="/booking/history" routerLinkActive="active" (click)="sidenav.close()">
              <mat-icon matListItemIcon>confirmation_number</mat-icon> My Bookings
            </a>
            <a mat-list-item routerLink="/payment/history" routerLinkActive="active" (click)="sidenav.close()">
              <mat-icon matListItemIcon>payment</mat-icon> Payments
            </a>
            <a mat-list-item routerLink="/profile" routerLinkActive="active" (click)="sidenav.close()">
              <mat-icon matListItemIcon>person</mat-icon> Profile
            </a>
            @if (auth.hasRole('ROLE_ADMIN')) {
              <a mat-list-item routerLink="/admin" routerLinkActive="active" (click)="sidenav.close()">
                <mat-icon matListItemIcon>admin_panel_settings</mat-icon> Admin
              </a>
            }
          } @else {
            <a mat-list-item routerLink="/auth/login" (click)="sidenav.close()">
              <mat-icon matListItemIcon>login</mat-icon> Login
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <app-toolbar (menuToggle)="sidenav.toggle()" />
        <main class="content">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .app-container { height: 100vh; }
    .sidenav { width: 250px; }
    .content { min-height: calc(100vh - 64px); }
    .active { background: rgba(255,255,255,0.08) !important; }
  `]
})
export class AppComponent {
  auth = inject(AuthService);
  isMobile = false;

  constructor() {
    const breakpointObserver = inject(BreakpointObserver);
    breakpointObserver.observe([Breakpoints.HandsetPortrait, Breakpoints.HandsetLandscape])
      .subscribe(result => this.isMobile = result.matches);
  }
}
