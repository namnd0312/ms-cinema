# Cinema Frontend Angular Application - Architecture Exploration Report

**Date**: 2026-03-22  
**Location**: /Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend  
**Total Source Files**: 74 TypeScript files, 32 components, 13 services

---

## 1. OVERALL ARCHITECTURE

### Framework & Build Setup
- **Angular 18.2** - Standalone components (no NgModules)
- **TypeScript 5.5.2** - Strict typing configured
- **SCSS** - Inline styling in components + global styles
- **Build**: Angular CLI with dev server supporting hot reload
- **Testing**: Karma + Jasmine configured but tests currently skipped

### State Management
- **Angular Signals** (v18+) - reactive local state using `signal()`, `computed()`, `effect()`
- **RxJS 7.8** - Observables for async operations, HTTP streams
- **No centralized store** (no NgRx/Akita) - state managed locally in services and components
- **Services as singletons** - injected with `providedIn: 'root'`

### Deployment
- **Docker**: Multi-stage build (Node 20 Alpine → Nginx Alpine)
- **Nginx**: Reverse proxy with SPA routing, WebSocket upgrade support
- **Production budget**: 1.5MB max bundle, output hashing enabled

---

## 2. APP ROUTING STRUCTURE

Root routes defined in `src/app/app.routes.ts` with lazy-loading feature modules:

| Path | Feature Module | Protected | Details |
|------|---|---|---|
| `/` | - | No | Redirects to `/movies` |
| `/auth/*` | auth | No | Login, register, OAuth2, password reset, activation |
| `/movies` | movies | No | Movie list & detail pages |
| `/booking/*` | booking | Yes (authGuard) | Seat selection, booking history, booking detail |
| `/payment/*` | payment | Yes (authGuard) | Stripe payment, payment history, status |
| `/profile/*` | profile | Yes (authGuard) | User profile, change password |
| `/admin/*` | admin | Yes (adminGuard) | Movie/theater/showtime management, payments |
| `/notifications` | notifications | No | Notification list |
| `**` | - | No | 404 Not Found |

### Route Guards
- **authGuard**: Checks `AuthService.isAuthenticated()` → redirects to login with returnUrl
- **adminGuard**: Checks role includes 'ROLE_ADMIN' → redirects to /movies if denied

---

## 3. CORE SERVICES & API ENDPOINTS

### Authentication Service (`auth.service.ts`)
**State**: `currentUser` (signal), `isAuthenticated` (computed)  
**Endpoints**:
- `POST /api/auth/login` - JWT login (stores token + refreshToken + user in localStorage)
- `POST /api/auth/register` - User registration
- `POST /api/auth/logout` - Revoke token
- `POST /api/auth/refresh-token` - Token refresh (handles 401 interceptor)
- `POST /api/auth/forgot-password` - Initiate password reset
- `POST /api/auth/reset-password` - Complete password reset
- `GET /api/auth/activate` - Email activation with token
- `POST /api/auth/resend-activation` - Resend activation email
- `POST /api/auth/change-password` - Change authenticated user password
- `GET /api/users/me` - Fetch current user profile (used in OAuth2 flow)

**JWT Flow**: AccessToken in Authorization header, RefreshToken stored for renewal

### Movie Service (`movie.service.ts`)
**Endpoints**:
- `GET /api/movies` - List all movies
- `GET /api/movies/{id}` - Fetch single movie
- `POST /api/movies` - Create (admin)
- `PUT /api/movies/{id}` - Update (admin)
- `DELETE /api/movies/{id}` - Delete (admin)
- `GET /api/showtimes` - List all showtimes (optionally filtered by movieId)
- `GET /api/showtimes/{id}` - Fetch single showtime
- `GET /api/showtimes/{showtimeId}/seats` - Fetch seats with price multiplier & type

### Booking Service (`booking.service.ts`)
**Endpoints**:
- `GET /api/bookings/showtimes/{showtimeId}/booked-seats` - Booked seat IDs
- `POST /api/bookings/reserve` - Reserve seats (BookingRequest: showtimeId, seatIds[])
- `GET /api/bookings/{id}` - Fetch booking details
- `GET /api/bookings/my` - User's bookings
- `POST /api/bookings/{id}/confirm` - Confirm after payment
- `POST /api/bookings/{id}/cancel` - Cancel booking

### Payment Service (`payment.service.ts`)
**Endpoints**:
- `POST /api/payments/create-intent` - Create Stripe PaymentIntent (returns clientSecret)
- `POST /api/payments/fake-success` - Dev endpoint for testing without Stripe
- `POST /api/payments/{id}/confirm` - Confirm payment completion
- `GET /api/payments/{id}` - Fetch payment
- `GET /api/payments/my` - User's payments
- `POST /api/payments/{id}/refund` - Refund payment
- `GET /api/payments` - All payments (admin)

**Integration**: Stripe.js v8.9 - uses `loadStripe()`, `stripe.elements()`, `confirmPayment()`

### Notification Services
**NotificationApiService** (`notification-api.service.ts`):
- `GET /api/notifications` - Paginated notifications (page, size params)
- `PATCH /api/notifications/{id}/read` - Mark single as read
- `PATCH /api/notifications/read-all` - Mark all as read
- `GET /api/notifications/unread-count` - Unread count
- `POST /api/notifications/broadcast` - Broadcast (admin)

**NotificationSseService** (`notification-sse.service.ts`):
- Establishes SSE stream: `EventSource('/api/notifications/stream?token={token}')`
- Events: 'notification', 'heartbeat', 'connected'
- Auto-reconnect: exponential backoff (1s → 2s → 4s ... → 30s max, 5 attempts)
- Emits via `newNotification` signal

### Movie Rating Service (`movie-rating.service.ts`)
**Endpoints**:
- `GET /api/movies/{movieId}/ratings` - Summary (averageRating, totalRatings, userRating)
- `POST /api/movies/{movieId}/ratings` - Submit rating (1-5)

### Movie Comment Service (`movie-comment.service.ts`)
**Endpoints**:
- `GET /api/movies/{movieId}/comments` - Paginated comments
- `POST /api/movies/{movieId}/comments` - Create comment
- `PUT /api/comments/{commentId}` - Update comment
- `DELETE /api/comments/{commentId}` - Delete comment
- `POST /api/comments/{commentId}/reactions` - Like/dislike
- `DELETE /api/comments/{commentId}/reactions` - Remove reaction

### Theater Service (`theater.service.ts`)
**Endpoints**:
- `GET /api/theaters` - List theaters
- `GET /api/theaters/{id}` - Fetch theater
- `POST /api/theaters` - Create (admin)
- `PUT /api/theaters/{id}` - Update (admin)

### Showtime Admin Service (`showtime-admin.service.ts`)
**Endpoints**:
- `POST /api/showtimes` - Create showtime (admin)
- `PUT /api/showtimes/{id}` - Update showtime (admin)

### Loading Service (`loading.service.ts`)
- Local request counter with `loading` signal
- Tracks HTTP requests (shown/hidden by loadingInterceptor)

### Seat WebSocket Service (`seat-websocket.service.ts`)
- **Transport**: STOMP over SockJS
- **URL**: `/ws` endpoint proxied to booking-service:8083
- **Topic**: `/topic/showtime/{showtimeId}/seats`
- **Message**: `{ seatIds: number[], status: string, timestamp: string }`
- **Status values**: RESERVED, CONFIRMED, RELEASED, LOCKED
- **Auto-disconnect**: Switches topic when loading different showtime

### Seat Suggestion Service (`seat-suggestion.service.ts`)
- **Client-side only** - finds adjacent available seat groups
- Scoring: center proximity (40%) + row sweet spot (30%) + type uniformity (30%)
- Returns top N suggestions with SeatGroup interface (seats[], score, rowLabel, totalPrice)

---

## 4. HTTP INTERCEPTORS

All interceptors in `src/app/core/interceptors/`:

### Auth Interceptor (`auth.interceptor.ts`)
- Attaches `Authorization: Bearer {token}` to non-auth requests
- **Public endpoints** (no token required): /api/auth/*, /api/bookings/showtimes/*, /api/movies, /api/showtimes
- **401 Handling**: 
  - Global `isRefreshing` flag prevents concurrent refresh calls
  - Uses `refreshTokenSubject` BehaviorSubject to queue requests during refresh
  - On success: retries original request with new token
  - On failure: calls `authService.logout()`
- Note: Auth endpoints skip token attach (prevents 401 when sending refresh token)

### Error Interceptor (`error.interceptor.ts`)
- Shows MatSnackBar error messages for HTTP errors
- Maps error codes to user-friendly messages
- Special handling:
  - 401: Handled by auth interceptor, no snackbar
  - 423: Account locked message
- Extracts error message from response body if available

### Loading Interceptor (`loading.interceptor.ts`)
- Calls `loadingService.show()` on request, `loadingService.hide()` on completion
- Uses `finalize()` operator for cleanup

---

## 5. MODELS & INTERFACES

All in `src/app/core/models/`:

### User Models (`user.model.ts`)
```typescript
interface User { id, username, email, fullName, roles[] }
interface LoginRequest { email, password }
interface RegisterRequest { username, email, password, fullName }
interface JwtResponse { id, token, refreshToken, email, username, name, roles[] }
interface TokenRefreshResponse { accessToken, refreshToken }
```

### Movie Models (`movie.model.ts`)
```typescript
interface Movie { id, title, description, durationMinutes, genre, releaseDate, posterUrl, rating, averageRating, totalRatings, commentCount }
interface Theater { id, name, location, totalRows, totalColumns, totalSeats, createdAt }
interface Showtime { id, movie, theater, startTime, endTime, basePrice }
interface Seat { id, seatLabel, seatType, rowNumber, columnNumber, price, status? }
```

### Booking Models (`booking.model.ts`)
```typescript
interface BookingSeat { id, seatId, seatLabel, seatType, price }
interface Booking { id, showtimeId, userId, status, totalAmount, reservedAt, confirmedAt, expiresAt, seats[] }
interface BookingRequest { showtimeId, seatIds[] }
```

### Payment Models (`payment.model.ts`)
```typescript
interface Payment { id, bookingId, userId, amount, currency, status, stripePaymentIntentId, createdAt, paidAt }
interface PaymentIntentRequest { bookingId, amount }
interface PaymentIntentResponse { paymentId, clientSecret, status }
```

### Notification Models (`notification.model.ts`)
```typescript
interface Notification { id, title, message, notificationType, isRead, createdAt }
interface NotificationPage { content[], totalElements, totalPages, number }
interface UnreadCountResponse { count }
```

### Movie Rating Models (`movie-rating.model.ts`)
```typescript
interface MovieRatingDto { id, movieId, userId, rating, createdAt }
interface MovieRatingSummaryDto { averageRating, totalRatings, userRating? }
interface CreateRatingRequest { rating }
```

### Movie Comment Models (`movie-comment.model.ts`)
```typescript
interface MovieCommentDto { id, movieId, userId, content, likeCount, dislikeCount, userReaction, createdAt, updatedAt }
interface CommentReactionDto { commentId, likeCount, dislikeCount, userReaction }
interface Page<T> { content[], totalElements, totalPages, size, number, first, last }
```

---

## 6. FEATURE MODULES & COMPONENTS

### Auth Feature (6 routes)
- **LoginComponent**: Email/password + Google OAuth2 button
- **RegisterComponent**: Account creation
- **ForgotPasswordComponent**: Email reset initiation
- **ResetPasswordComponent**: Token-based password reset
- **ActivateAccountComponent**: Email activation link
- **OAuth2CallbackComponent**: OAuth2 token handling (calls `authService.handleOAuth2Callback()`)

### Movies Feature (2 routes)
- **MovieListComponent**: Grid of movie cards with search filter
- **MovieCardComponent**: Card with poster, title, rating badge
- **MovieDetailComponent**: Full info, showtimes, ratings, comments
- **StarRatingComponent**: Interactive 5-star rating
- **CommentListComponent**: Paginated comments with reactions
- **CommentItemComponent**: Individual comment display

### Booking Feature (3 routes, auth-protected)
- **SeatSelectionComponent**: 
  - Multi-step stepper (Select → Review → Payment redirect)
  - Real-time WebSocket seat updates
  - Suggestion panel integration
  - Mobile-responsive bottom summary bar
  - Countdown timer for reservation expiry
- **SeatGridComponent**: 
  - Accessible grid with ARIA roles + keyboard navigation (arrow keys)
  - Sections (VIP/Premium/Standard) with visual dividers
  - Roving tabindex focus management
  - Legend showing seat types & prices
  - 3D perspective styling
- **BookingSummaryComponent**: Displays selected seats, total price, showtime
- **SeatSuggestionPanelComponent**: Group size input, suggestion list, quick-select
- **BookingHistoryComponent**: User's bookings with status
- **BookingDetailComponent**: Single booking info with seats

### Payment Feature (3 routes, auth-protected)
- **PaymentPageComponent**: 
  - Stripe PaymentElement (dynamic method selection)
  - Booking summary
  - Client-side Stripe integration (`stripe.confirmPayment()`)
  - Fallback to fake payment endpoint for dev
- **PaymentStatusComponent**: Success/failure confirmation page
- **PaymentHistoryComponent**: User's payment history

### Profile Feature (2 routes, auth-protected)
- **ProfilePageComponent**: User info display
- **ChangePasswordComponent**: Current + new password form

### Admin Feature (4 sub-routes, adminGuard)
- **AdminNavComponent**: Navigation between admin panels
- **MovieManagementComponent**: CRUD for movies
- **TheaterManagementComponent**: CRUD for theaters
- **ShowtimeManagementComponent**: CRUD for showtimes
- **PaymentManagementComponent**: View all payments

### Notifications Feature (1 route)
- **NotificationListComponent**: Paginated notifications with mark-as-read

### Shared Components
- **ToolbarComponent**: Top navigation with logo, links, user menu, notification bell
- **NotificationBellComponent**: Bell icon with unread badge, SSE listener
- **LoadingBarComponent**: Full-width progress bar (signals loading state)
- **NotFoundComponent**: 404 page

---

## 7. AUTHENTICATION & AUTHORIZATION

### JWT Flow
1. **Login**: POST /api/auth/login → receives { token, refreshToken, id, username, email, name, roles }
2. **Storage**: Tokens in localStorage (TOKEN_KEY, REFRESH_KEY), user in localStorage (USER_KEY)
3. **Session**: AuthService loads stored user on init via `loadStoredUser()`
4. **Persistence**: `currentUser` signal, `isAuthenticated` computed
5. **Token Refresh**: 
   - 401 interceptor triggers auto-refresh
   - Uses refreshToken from storage
   - Retries failed request with new accessToken

### OAuth2 Flow
1. **Initiate**: LoginComponent button → redirects to `{gatewayUrl}/oauth2/authorization/google`
2. **Callback**: OAuth2CallbackComponent receives token + refreshToken as query params
3. **Handle**: `authService.handleOAuth2Callback()` stores tokens + fetches user profile
4. **Redirect**: Router navigates to /movies or returnUrl

### Role-Based Access
- Roles stored as string[] in User model
- `AuthService.hasRole(role)` checks membership
- adminGuard checks for 'ROLE_ADMIN'
- Admin routes conditionally shown in toolbar/sidenav

---

## 8. WEBSOCKET INTEGRATION

### Seat Status Updates
- **Service**: SeatWebSocketService
- **URL**: /ws (proxied to booking-service:8083)
- **Protocol**: STOMP + SockJS (fallback for browsers without native WebSocket)
- **Topic per showtime**: `/topic/showtime/{showtimeId}/seats`
- **Message schema**: `{ seatIds: number[], status: string, timestamp: string }`
- **Usage**: SeatSelectionComponent subscribes on init, auto-disconnects on destroy
- **State sync**: Incoming updates mutate seats[] signal, de-select conflicting selections

### Reconnection Strategy
- Auto-reconnect enabled in STOMP client (5s delay)
- Cleans up on navigation to different showtime
- Unsubscribed in ngOnDestroy to prevent memory leaks

---

## 9. UI FRAMEWORK & STYLING

### Material Design
- **@angular/material 18.2.14** for UI components
- **Dark theme** with custom color palette:
  - Primary: Violet (#1a237e-ish via mat.$violet-palette)
  - Tertiary/Accent: Orange (#ffc107, mat.$orange-palette)
  - Background: #121212
  - Surface: #1e1e2f
- **Components used**: 
  - Toolbar, Sidenav, Card, Form, Button, Stepper
  - Snackbar, Dialog, Menu, Chips, Badge, Divider, List
  - Progress spinner, Tooltip, Icon

### Responsive Design
- **Mobile-first** media queries (max-width: 600px)
- **Tablet**: max-width: 960px
- **Breakpoint observer** in AppComponent for Handset detection
- **Sticky toolbar**, collapsible sidenav, adaptive layouts

### Accessibility
- **ARIA roles**: grid, gridcell, row, rowheader, rowgroup
- **Keyboard nav**: Arrow keys in seat grid (roving tabindex)
- **Live regions**: Announcements on seat selection
- **Labels**: Proper matLabels on form fields
- **Focus management**: Visible focus indicators, tab order

### SCSS Features
- **Global styles** in `src/styles.scss` (Material theme, dark mode variables)
- **Component styles**: Scoped inline SCSS in component decorator
- **Variables**: --cinema-primary, --cinema-accent, --cinema-bg
- **No CSS frameworks** beyond Material (no Bootstrap, Tailwind)

---

## 10. KEY THIRD-PARTY DEPENDENCIES

| Package | Version | Purpose |
|---------|---------|---------|
| @angular/material | 18.2.14 | UI components & theming |
| @angular/cdk | 18.2.14 | Layout, breakpoints, a11y utilities |
| @stomp/stompjs | 7.3.0 | STOMP WebSocket protocol |
| sockjs-client | 1.6.1 | WebSocket fallback |
| @stripe/stripe-js | 8.9.0 | Stripe payment integration |
| rxjs | 7.8.0 | Reactive programming |
| zone.js | 0.14.10 | Angular change detection |

**Dev**: Karma, Jasmine, TypeScript 5.5.2, Angular CLI 18.2.21

---

## 11. BUILD & DEPLOYMENT

### Development Server
- **Command**: `npm start` (ng serve)
- **Proxy**: proxy.conf.json routes /api → localhost:8080, /ws → localhost:8083
- **Port**: localhost:4200
- **Hot reload**: Enabled

### Production Build
- **Command**: `npm run build`
- **Output**: dist/cinema-frontend/browser
- **Budgets**: 
  - Initial: 1.5MB error (800KB warning)
  - Component styles: 4KB error (2KB warning)
- **Optimizations**: Output hashing, tree-shaking, minification

### Docker Deployment
```dockerfile
Stage 1 (build): Node 20 Alpine
  - npm ci
  - npx ng build --configuration=production

Stage 2 (runtime): Nginx Alpine
  - Copy nginx.conf
  - Copy dist/cinema-frontend/browser to /usr/share/nginx/html
  - Expose port 80
```

### Nginx Configuration (nginx.conf)
- **SPA routing**: try_files $uri $uri/ /index.html (fallback for client-side routing)
- **API proxy**: /api/ → http://api-gateway:8080/api/
- **WebSocket proxy**: /ws/ → http://booking-service:8083/ws/ (with WebSocket upgrade headers)
- **OAuth2**: /oauth2/ and /login/oauth2/ → api-gateway:8080

---

## 12. STATE MANAGEMENT PATTERN

### Signal-Based Reactive State
**Example from SeatSelectionComponent**:
```typescript
seats = signal<Seat[]>([]);
selectedSeatIds = signal<Set<number>>(new Set());
totalPrice = computed(() => 
  this.selectedSeatsArray().reduce((sum, s) => sum + s.price, 0)
);
```

**Characteristics**:
- **Granular reactivity**: Each signal independently tracks changes
- **Computed properties**: Auto-update when inputs change (no manual subscription)
- **No boilerplate**: No reducers, actions, or dispatch calls
- **Type-safe**: Full TypeScript inference
- **Performance**: Fine-grained change detection in Angular 18+

### Service State Pattern
**Example from AuthService**:
```typescript
currentUser = signal<User | null>(null);
isAuthenticated = computed(() => !!this.currentUser());
```
- Services as singleton stores
- localStorage persistence for auth data
- Signals exposed publicly for components to subscribe

### Local Component State
Most state stays in components, lifted to services only when shared across routes.

---

## 13. BUILD OUTPUT

**Total TypeScript files**: 74 (components, services, guards, interceptors, models)
**Standalone components**: 32 (all use standalone: true)
**Services**: 13 core services
**Feature modules**: 7 (auth, movies, booking, payment, profile, admin, notifications)
**Shared components**: 4 (toolbar, notification-bell, loading-bar, not-found)

---

## 14. CONFIGURATIONS

### angular.json
- **Test builder**: Karma configured but tests skipped (`skipTests: true`)
- **Styles**: SCSS, global styles + Material theming
- **Assets**: Public folder copied to dist
- **Source maps**: Enabled in development

### TypeScript (tsconfig.json, tsconfig.app.json)
- Modern ES2022 target
- Strict mode enabled
- Module resolution: node
- App file routing in src/app

### Environment (`environment.ts`)
```typescript
{
  production: false,
  apiUrl: '/api',
  gatewayUrl: 'http://localhost:8080',
  stripePublishableKey: 'pk_test_...'
}
```

---

## 15. KEY ARCHITECTURAL DECISIONS

1. **Standalone Components**: Modern Angular (v14+) pattern, no module declarations
2. **Signals over RxJS Subjects**: Simpler local state management for most use cases
3. **Service Singletons**: Injected with `providedIn: 'root'` for tree-shaking
4. **Lazy Loading**: Feature modules loaded on-demand via Router
5. **WebSocket via STOMP**: Decouples from direct WebSocket API, supports fallback
6. **Client-side Suggestions**: Seat grouping algorithm runs locally (no server call)
7. **Token Refresh Interceptor**: Automatic token renewal on 401 (transparent to components)
8. **Dark Material Theme**: Enforced globally, cinema branding via CSS variables
9. **Multi-step Booking Flow**: Stepper UX guides through seat selection → review → payment
10. **Responsive Mobile-First**: Separate layouts for mobile sidenav vs desktop

---

## 16. TESTING SETUP

- **Framework**: Karma + Jasmine
- **Status**: Tests currently skipped (`skipTests: true` in angular.json)
- **Config**: tsconfig.spec.json points to spec files
- **Run**: `npm test`
- **Note**: No test files found in codebase (*.spec.ts missing)

---

## 17. SECURITY CONSIDERATIONS

- **HTTPS in production**: Nginx config ready for SSL termination
- **JWT stored in localStorage**: Vulnerable to XSS; consider httpOnly cookies for refresh token
- **CORS headers**: Set by backend (api-gateway:8080)
- **No sensitive data in localStorage**: Only token, refreshToken, user info (public)
- **Rate limiting**: Not implemented on frontend (backend responsibility)
- **CSRF**: Not explicitly visible (depends on backend token validation)

---

## 18. PERFORMANCE OPTIMIZATIONS

- **Lazy loading**: Feature modules & images
- **OnPush change detection**: Implicit with Signals (component only updates when signals change)
- **Tree-shaking**: Unused code removed in production
- **Bundle budgets**: 1.5MB max enforced
- **Caching**: HTTPClient caching via backend headers (no explicit frontend caching)
- **Pagination**: Notifications, comments implement page-based loading

---

## 19. ACCESSIBILITY FEATURES

- **Semantic HTML**: Proper heading hierarchy, labels
- **ARIA attributes**: grid roles, live regions, aria-label, aria-pressed
- **Keyboard navigation**: Full keyboard support in seat grid
- **Focus management**: Visible focus indicators, roving tabindex pattern
- **Color contrast**: Material dark theme meets WCAG AA
- **Screen reader announcements**: Live region for seat selection feedback

---

## 20. KNOWN LIMITATIONS & GAPS

**No explicit state management**: For complex multi-component state, consider implementing a store pattern

**No error boundary**: Unhandled errors could crash app (consider ErrorHandler provider)

**No build analysis**: No bundle analyzer configured (consider webpack-bundle-analyzer)

**Limited testing**: Test suite not implemented despite Karma/Jasmine setup

**Hardcoded API base**: Uses /api proxy; production may need environment-specific URLs

**No service worker**: No offline support or PWA capabilities

---

## File Structure Summary
```
cinema-frontend/
├── Dockerfile (multi-stage build)
├── nginx.conf (SPA + API + WS routing)
├── proxy.conf.json (dev server proxies)
├── package.json (v18 Angular, Material, Stripe)
├── angular.json (build config)
├── tsconfig.json (TypeScript config)
├── src/
│   ├── main.ts (bootstrap AppComponent)
│   ├── styles.scss (global + Material theme)
│   ├── app/
│   │   ├── app.component.ts (layout, sidenav)
│   │   ├── app.config.ts (providers: router, http, animations)
│   │   ├── app.routes.ts (lazy-loaded features)
│   │   ├── core/
│   │   │   ├── services/ (13 .ts files)
│   │   │   ├── interceptors/ (3: auth, error, loading)
│   │   │   ├── models/ (7 interfaces)
│   │   │   └── guards/ (2: auth, admin)
│   │   ├── features/
│   │   │   ├── auth/ (6 components)
│   │   │   ├── movies/ (6 components)
│   │   │   ├── booking/ (6 components)
│   │   │   ├── payment/ (3 components)
│   │   │   ├── profile/ (2 components)
│   │   │   ├── admin/ (5 components)
│   │   │   └── notifications/ (1 component)
│   │   └── shared/
│   │       └── components/ (4: toolbar, bell, bar, 404)
│   └── environments/
│       └── environment.ts (API URLs, Stripe key)
└── dist/ (compiled output)
```

---

## Summary

The **cinema-frontend** is a modern, feature-rich Angular 18 SPA with:
- **Standalone components** for modularity & tree-shaking
- **Signal-based state** for reactive, granular updates
- **Comprehensive services** covering auth (JWT + OAuth2), movies, bookings, payments, notifications
- **Real-time WebSocket** for seat availability sync
- **Stripe payment integration** with PaymentElement
- **Material Design dark theme** with accessibility & responsiveness
- **Multi-layered architecture**: interceptors, guards, lazy-loaded features
- **Responsive UI** with mobile-first breakpoints & keyboard nav
- **Production-ready Nginx** deployment with WebSocket support

**Architecture strength**: Clean separation, reusable services, DI-based injection, minimal boilerplate via Signals.

