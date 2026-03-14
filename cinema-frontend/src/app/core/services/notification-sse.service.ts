import { Injectable, inject, signal, OnDestroy } from '@angular/core';
import { AuthService } from './auth.service';
import { Notification } from '../models/notification.model';

/**
 * Manages SSE connection for real-time in-app notifications.
 * Connects when authenticated, disconnects on logout.
 * Exponential backoff reconnect: 1s → 2s → 4s... → 30s max, 5 attempts.
 */
@Injectable({ providedIn: 'root' })
export class NotificationSseService implements OnDestroy {
  private authService = inject(AuthService);
  private eventSource: EventSource | null = null;
  private reconnectAttempts = 0;
  private readonly maxAttempts = 5;
  private reconnectTimeout: any = null;

  /** Emits the latest notification received via SSE. */
  newNotification = signal<Notification | null>(null);

  connect(): void {
    this.disconnect();
    const token = this.authService.getToken();
    if (!token) return;

    this.eventSource = new EventSource(`/api/notifications/stream?token=${token}`);

    this.eventSource.addEventListener('notification', (event: any) => {
      const notification: Notification = JSON.parse(event.data);
      this.newNotification.set(notification);
      this.reconnectAttempts = 0;
    });

    this.eventSource.addEventListener('heartbeat', () => {
      // Keep-alive, no action needed
    });

    this.eventSource.addEventListener('connected', () => {
      this.reconnectAttempts = 0;
    });

    this.eventSource.onerror = () => {
      this.eventSource?.close();
      this.eventSource = null;
      this.handleReconnect();
    };
  }

  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxAttempts) return;
    if (!this.authService.isAuthenticated()) return;

    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectTimeout = setTimeout(() => this.connect(), delay);
  }

  disconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
    this.eventSource?.close();
    this.eventSource = null;
    this.reconnectAttempts = 0;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
