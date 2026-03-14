import { Component, inject, effect, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { take } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatBadgeModule } from '@angular/material/badge';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationSseService } from '../../../core/services/notification-sse.service';
import { NotificationApiService } from '../../../core/services/notification-api.service';

/**
 * Notification bell icon with unread badge in the toolbar.
 * Connects SSE on auth, disconnects on logout.
 * Shows snackbar toast on new notification arrival.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatBadgeModule, MatSnackBarModule],
  template: `
    <button mat-icon-button (click)="goToNotifications()"
            [matBadge]="unreadCount"
            [matBadgeHidden]="unreadCount === 0"
            matBadgeColor="warn" matBadgeSize="small"
            matBadgeOverlap="true">
      <mat-icon>{{ unreadCount > 0 ? 'notifications_active' : 'notifications' }}</mat-icon>
    </button>
  `,
  styles: [`
    :host { display: inline-flex; }
    button { color: inherit; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private auth = inject(AuthService);
  private sseService = inject(NotificationSseService);
  private apiService = inject(NotificationApiService);
  private snackBar = inject(MatSnackBar);
  private router = inject(Router);

  unreadCount = 0;

  constructor() {
    // React to new SSE notifications
    effect(() => {
      const notification = this.sseService.newNotification();
      if (notification) {
        this.unreadCount++;
        this.snackBar.open(notification.title, 'View', { duration: 5000 })
          .onAction().pipe(take(1)).subscribe(() => this.goToNotifications());
      }
    });
  }

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.sseService.connect();
      this.fetchUnreadCount();
    }
  }

  ngOnDestroy(): void {
    this.sseService.disconnect();
  }

  goToNotifications(): void {
    this.router.navigate(['/notifications']);
  }

  private fetchUnreadCount(): void {
    this.apiService.getUnreadCount().subscribe({
      next: res => this.unreadCount = res.count,
      error: () => {}
    });
  }
}
