import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { NotificationApiService } from '../../../core/services/notification-api.service';
import { Notification } from '../../../core/models/notification.model';

/**
 * Paginated notification list page with mark-as-read functionality.
 * Shows notification title, message, time, and read status.
 */
@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [CommonModule, MatListModule, MatButtonModule, MatIconModule, MatCardModule, MatPaginatorModule],
  template: `
    <div class="notification-page">
      <div class="header">
        <h2>Notifications</h2>
        <button mat-stroked-button (click)="markAllAsRead()" [disabled]="notifications.length === 0">
          <mat-icon>done_all</mat-icon> Mark all as read
        </button>
      </div>

      @if (notifications.length === 0) {
        <div class="empty-state">
          <mat-icon>notifications_none</mat-icon>
          <p>No notifications yet</p>
        </div>
      } @else {
        <div class="notification-list">
          @for (n of notifications; track n.id) {
            <mat-card class="notification-card" [class.unread]="!n.isRead"
                      [class.success]="n.notificationType === 'PAYMENT_SUCCESS'"
                      [class.failed]="n.notificationType === 'PAYMENT_FAILED'"
                      [class.broadcast]="n.notificationType === 'ADMIN_BROADCAST'"
                      (click)="markAsRead(n)">
              <div class="notification-row">
                <mat-icon class="type-icon"
                  [class.icon-success]="n.notificationType === 'PAYMENT_SUCCESS'"
                  [class.icon-failed]="n.notificationType === 'PAYMENT_FAILED'"
                  [class.icon-broadcast]="n.notificationType === 'ADMIN_BROADCAST'">
                  {{ n.notificationType === 'PAYMENT_SUCCESS' ? 'check_circle' :
                     n.notificationType === 'PAYMENT_FAILED' ? 'error' :
                     n.notificationType === 'ADMIN_BROADCAST' ? 'campaign' : 'info' }}
                </mat-icon>
                <div class="notification-content">
                  <div class="title" [class.bold]="!n.isRead">{{ n.title }}</div>
                  <div class="message">{{ n.message }}</div>
                </div>
                <div class="meta">
                  <span class="time">{{ formatTime(n.createdAt) }}</span>
                  @if (!n.isRead) { <span class="unread-dot"></span> }
                </div>
              </div>
            </mat-card>
          }
        </div>

        <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageIndex]="currentPage"
                       (page)="onPageChange($event)" [pageSizeOptions]="[10, 20, 50]">
        </mat-paginator>
      }
    </div>
  `,
  styles: [`
    .notification-page { max-width: 800px; margin: 24px auto; padding: 0 16px; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .notification-list { display: flex; flex-direction: column; gap: 8px; }
    .notification-card { cursor: pointer; transition: box-shadow 0.2s; }
    .notification-card:hover { box-shadow: 0 2px 12px rgba(255,255,255,0.08); }
    .notification-card.unread { border-left: 4px solid #7c4dff; }
    .notification-card.unread.success { border-left-color: #66bb6a; background: rgba(102,187,106,0.08); }
    .notification-card.unread.failed { border-left-color: #ef5350; background: rgba(239,83,80,0.08); }
    .notification-card.unread.broadcast { border-left-color: #ffa726; background: rgba(255,167,38,0.08); }
    .notification-row { display: flex; align-items: flex-start; gap: 12px; padding: 12px; }
    .type-icon { flex-shrink: 0; margin-top: 2px; }
    .icon-success { color: #66bb6a; }
    .icon-failed { color: #ef5350; }
    .icon-broadcast { color: #ffa726; }
    .notification-content { flex: 1; min-width: 0; }
    .title { font-size: 0.95rem; }
    .bold { font-weight: 600; color: #fff; }
    .message { font-size: 0.85rem; color: rgba(255,255,255,0.6); margin-top: 4px; }
    .meta { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; flex-shrink: 0; }
    .time { font-size: 0.8rem; color: rgba(255,255,255,0.4); white-space: nowrap; }
    .unread-dot { width: 8px; height: 8px; border-radius: 50%; background: #7c4dff; }
    .empty-state { text-align: center; padding: 48px; color: rgba(255,255,255,0.4); }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: rgba(255,255,255,0.2); }
  `]
})
export class NotificationListComponent implements OnInit {
  private apiService = inject(NotificationApiService);

  notifications: Notification[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.apiService.getNotifications(this.currentPage, this.pageSize).subscribe({
      next: page => {
        this.notifications = page.content;
        this.totalElements = page.totalElements;
      }
    });
  }

  markAsRead(n: Notification): void {
    if (n.isRead) return;
    this.apiService.markAsRead(n.id).subscribe({
      next: () => { n.isRead = true; }
    });
  }

  markAllAsRead(): void {
    this.apiService.markAllAsRead().subscribe({
      next: () => {
        this.notifications.forEach(n => n.isRead = true);
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadNotifications();
  }

  formatTime(dateStr: string): string {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }
}
