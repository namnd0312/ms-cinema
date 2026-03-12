# Phase 5: Angular Frontend — SSE Client, Notification Bell, and List Page

## Context Links
- [auth.service.ts](../../cinema-frontend/src/app/core/services/auth.service.ts)
- [auth.interceptor.ts](../../cinema-frontend/src/app/core/interceptors/auth.interceptor.ts)
- [user.model.ts](../../cinema-frontend/src/app/core/models/user.model.ts)
- [app.routes.ts](../../cinema-frontend/src/app/app.routes.ts)
- [Kafka Notification Research — Angular section](./research/researcher-kafka-notification-pattern.md)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P2
- **Status:** pending
- **Effort:** 1.5h

Build Angular SSE client service, notification bell icon with unread badge in toolbar, and notification list page. Uses native `EventSource` API with exponential backoff reconnect. Connects when user is authenticated, disconnects on logout.

## Key Insights
- Angular 18 with standalone components, signals, inject() pattern
- Auth stores JWT in localStorage via `AuthService.getToken()`
- `EventSource` does NOT support custom headers — pass JWT as query param
- User model has `id` (number) — used as userId for SSE connection
- Existing interceptor handles 401 refresh for REST calls but NOT for EventSource
- Material 18 available for badge, icon, list components
- Lazy-loaded routes pattern: add `/notifications` route

## Requirements

### Functional
- `NotificationSseService`: connect to SSE, emit notifications via BehaviorSubject/signal
- `NotificationApiService`: REST calls for history, mark-as-read, unread count, broadcast
- Notification bell component in app toolbar:
  - Mat-icon `notifications` with badge showing unread count
  - Click opens dropdown or navigates to notification list
- Notification list page:
  - Paginated list of notifications (title, message, time ago, read/unread)
  - Mark individual as read on click
  - "Mark all as read" button
- Auto-connect SSE on login, disconnect on logout
- Notification model interface

### Non-functional
- Exponential backoff reconnect (1s → 2s → 4s... → 30s max, 5 attempts)
- Unread count updates in real-time via SSE events
- Toast/snackbar on new notification arrival

## Architecture

```
cinema-frontend/src/app/
├── core/
│   ├── models/
│   │   └── notification.model.ts              ← NEW
│   └── services/
│       ├── notification-sse.service.ts         ← NEW
│       └── notification-api.service.ts         ← NEW
├── shared/
│   └── components/
│       └── notification-bell/
│           ├── notification-bell.component.ts  ← NEW
│           └── notification-bell.component.html ← NEW
└── features/
    └── notifications/
        ├── notifications.routes.ts             ← NEW
        └── notification-list/
            ├── notification-list.component.ts  ← NEW
            └── notification-list.component.html ← NEW
```

## Related Code Files

### Files to Create (8 files)
1. `core/models/notification.model.ts` — Notification interface + NotificationType enum
2. `core/services/notification-sse.service.ts` — EventSource connection + reconnect
3. `core/services/notification-api.service.ts` — REST API calls
4. `shared/components/notification-bell/notification-bell.component.ts` — bell icon + badge
5. `shared/components/notification-bell/notification-bell.component.html` — template
6. `features/notifications/notifications.routes.ts` — lazy route config
7. `features/notifications/notification-list/notification-list.component.ts` — list page
8. `features/notifications/notification-list/notification-list.component.html` — list template

### Files to Modify
1. `app.routes.ts` — add notifications lazy route
2. App toolbar/header component — add `<app-notification-bell>` (find existing toolbar)
3. `auth.interceptor.ts` — add `/api/notifications` to PUBLIC_URLS for SSE endpoint

## Implementation Steps

### Step 1: Create Notification Model

`core/models/notification.model.ts`:
```typescript
export interface Notification {
  id: number;
  title: string;
  message: string;
  notificationType: string;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationPage {
  content: Notification[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface UnreadCountResponse {
  count: number;
}
```

### Step 2: Create NotificationSseService

`core/services/notification-sse.service.ts`:
```typescript
@Injectable({ providedIn: 'root' })
export class NotificationSseService {
  private authService = inject(AuthService);
  private eventSource: EventSource | null = null;
  private reconnectAttempts = 0;
  private maxAttempts = 5;

  newNotification = signal<Notification | null>(null);

  connect(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventSource = new EventSource(
      `/api/notifications/stream?token=${token}`
    );

    this.eventSource.addEventListener('notification', (event: any) => {
      const notification = JSON.parse(event.data);
      this.newNotification.set(notification);
      this.reconnectAttempts = 0;
    });

    this.eventSource.addEventListener('heartbeat', () => {
      // Keep-alive, no action needed
    });

    this.eventSource.onerror = () => {
      this.eventSource?.close();
      this.handleReconnect();
    };
  }

  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxAttempts) return;
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    setTimeout(() => this.connect(), delay);
  }

  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
    this.reconnectAttempts = 0;
  }
}
```

### Step 3: Create NotificationApiService

`core/services/notification-api.service.ts`:
```typescript
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
```

### Step 4: Create NotificationBellComponent

Standalone component using Material `mat-icon` and `mat-badge`:
- Inject `NotificationSseService` and `NotificationApiService`
- On init: fetch unread count, connect SSE
- On new notification signal effect: increment unread count, show snackbar
- On click: navigate to `/notifications`
- On destroy or logout: disconnect SSE

### Step 5: Create NotificationListComponent

Standalone component:
- Paginated list with `mat-list` or `mat-card`
- Each item: title (bold if unread), message, time-ago pipe, read indicator
- Click item → mark as read
- "Mark all as read" button at top
- Infinite scroll or paginator

### Step 6: Add Notifications Route

`features/notifications/notifications.routes.ts`:
```typescript
export const NOTIFICATIONS_ROUTES: Routes = [
  { path: '', component: NotificationListComponent }
];
```

### Step 7: Update app.routes.ts

Add lazy-loaded route:
```typescript
{
  path: 'notifications',
  loadChildren: () => import('./features/notifications/notifications.routes')
    .then(m => m.NOTIFICATIONS_ROUTES)
}
```

### Step 8: Add Bell to App Toolbar

Find the existing app toolbar/header component and add `<app-notification-bell>` next to user menu. Only show when authenticated.

### Step 9: Connect/Disconnect SSE on Auth State Change

In NotificationBellComponent or a top-level effect:
- Watch `authService.isAuthenticated` signal
- On true → connect SSE + fetch unread count
- On false → disconnect SSE

## Todo List
- [ ] Create notification.model.ts
- [ ] Create notification-sse.service.ts
- [ ] Create notification-api.service.ts
- [ ] Create notification-bell component (ts + html)
- [ ] Create notification-list component (ts + html)
- [ ] Create notifications.routes.ts
- [ ] Update app.routes.ts with notifications route
- [ ] Add notification bell to app toolbar
- [ ] Wire SSE connect/disconnect to auth state
- [ ] Verify with `ng build` or `ng serve`

## Success Criteria
- SSE connects when user logs in, disconnects on logout
- New notifications appear as snackbar toast
- Bell icon shows unread badge count, updates in real-time
- Notification list page shows paginated history
- Mark as read / mark all as read works
- Reconnects with exponential backoff on connection drop

## Risk Assessment
- **Token expiry during SSE**: EventSource auto-reconnects; new connect uses fresh token from localStorage (refreshed by interceptor on REST calls). If token expired and no refresh happened, SSE will fail auth — user must interact with REST to trigger refresh first. Acceptable tradeoff.
- **Memory leak**: EventSource.close() in disconnect + ngOnDestroy prevents leaks

## Security Considerations
- JWT passed as query param — visible in browser network tab (same as standard auth flow)
- Do NOT log token in console
- SSE endpoint validates JWT server-side; expired tokens rejected with 401
- Broadcast endpoint admin-only; frontend hides broadcast UI for non-admin users

## Next Steps
- Phase 6: Docker + config-server updates for notificationdb
