import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NotificationPage, UnreadCountResponse } from '../models/notification.model';

/** REST API service for notification CRUD operations. */
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private http = inject(HttpClient);

  getNotifications(page = 0, size = 20): Observable<NotificationPage> {
    return this.http.get<NotificationPage>('/api/notifications', { params: { page, size } });
  }

  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`/api/notifications/${id}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>('/api/notifications/read-all', {});
  }

  getUnreadCount(): Observable<UnreadCountResponse> {
    return this.http.get<UnreadCountResponse>('/api/notifications/unread-count');
  }

  broadcast(title: string, message: string): Observable<void> {
    return this.http.post<void>('/api/notifications/broadcast', { title, message });
  }
}
