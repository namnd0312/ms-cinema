# Admin Features Exploration Report
**Date:** 2026-03-12 | **Thoroughness:** Very Thorough

## Executive Summary
MS Cinema has **admin capabilities implemented** across backend (6 admin-protected endpoints) and frontend (theater & showtime management UI). Admin features enable:
- Movie catalog management (CRUD)
- Theater management (CRUD)
- Showtime scheduling (CRUD)
- Payment refunds
- Comment moderation

Frontend admin interface is **partial** — displays read-only lists but lacks create/update/delete UI forms.

---

## 1. Backend Admin Endpoints (All Protected with @PreAuthorize)

### Movie Service (4 admin endpoints)
**Endpoint Base:** `/api/movies`, `/api/theaters`, `/api/showtimes`

| Endpoint | Method | Role | Purpose | Notes |
|----------|--------|------|---------|-------|
| `/api/movies` | POST | ADMIN | Create movie | Returns MovieDto |
| `/api/movies/{id}` | PUT | ADMIN | Update movie | Returns MovieDto |
| `/api/movies/{id}` | DELETE | ADMIN | Delete movie | Returns 204 No Content |
| `/api/theaters` | POST | ADMIN | Create theater | Auto-generates seat grid |
| `/api/theaters/{id}` | PUT | ADMIN | Update theater | Returns TheaterDto |
| `/api/showtimes` | POST | ADMIN | Create showtime | Requires movie + theater + startTime |
| `/api/showtimes/{id}` | PUT | ADMIN | Update showtime | Returns ShowtimeDto |

**Files:**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/movie-service/src/main/java/com/namnd/movieservice/controller/MovieController.java`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/movie-service/src/main/java/com/namnd/movieservice/controller/TheaterController.java`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/movie-service/src/main/java/com/namnd/movieservice/controller/ShowtimeController.java`

### Payment Service (1 admin endpoint)
| Endpoint | Method | Role | Purpose |
|----------|--------|------|---------|
| `/api/payments/{id}/refund` | POST | ADMIN | Refund payment |

**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/payment-service/src/main/java/com/namnd/paymentservice/controller/PaymentController.java`

### Auth Service (1 test endpoint)
| Endpoint | Method | Role | Purpose |
|----------|--------|------|---------|
| `/api/test/admin` | GET | ADMIN | Role-based access test |

**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/controller/TestController.java`

### Movie Comments (Comment moderation via role check)
**Endpoint:** `/api/comments/{commentId}` (DELETE)
- **Auth:** `@PreAuthorize("isAuthenticated()")`
- **Role Check:** Backend checks `user.roles().contains("ROLE_ADMIN")` to allow deletion of any comment
- **File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/movie-service/src/main/java/com/namnd/movieservice/controller/MovieCommentController.java:71`
- **Logic:** Admin can delete any comment; non-admin can only delete own comments

---

## 2. Database Role Structure

**Location:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/resources/roles.sql`

**Available Roles:**
```sql
ROLE_USER   -- Regular user
ROLE_PM     -- Project manager (intermediate access)
ROLE_ADMIN  -- Full admin access
```

**Schema (auth-service database):**
- `roles` table: Stores role definitions
- `user_roles` junction table: Maps users to roles

---

## 3. Frontend Admin Implementation

### Route Structure
**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/app.routes.ts`
```
/admin
  ├── /theaters → TheaterManagementComponent
  └── /showtimes → ShowtimeManagementComponent
```

### Admin Guard
**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/core/guards/admin.guard.ts`
- Checks: `authService.isAuthenticated() && authService.hasRole('ROLE_ADMIN')`
- Redirects non-admins to `/movies`

### Admin Components

#### TheaterManagementComponent
**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/features/admin/theater-management/theater-management.component.ts`
- **Functionality:** Read-only theater list display
- **Features:**
  - Fetches `/api/theaters` on init
  - Displays theater name + seat count in Material cards
  - Shows loading spinner during fetch
  - Empty state message

#### ShowtimeManagementComponent  
**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/features/admin/showtime-management/showtime-management.component.ts`
- **Functionality:** Read-only showtime list display
- **Features:**
  - Fetches showtimes via MovieService
  - Displays movie title, theater, date/time, price
  - Formatted with DatePipe & CurrencyPipe
  - Material card layout

### Navigation UI

**Files Showing Admin Link:**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/shared/components/toolbar/toolbar.component.ts:27-28` — Toolbar nav link
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/app.component.ts:34-37` — Sidenav menu

**Visibility Logic:**
```typescript
@if (auth.hasRole('ROLE_ADMIN')) {
  <a mat-button routerLink="/admin">Admin</a>
}
```

### AuthService Role Checking
**File:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/core/services/auth.service.ts:106-108`
```typescript
hasRole(role: string): boolean {
  return this.currentUser()?.roles?.includes(role) ?? false;
}
```

### Comment Moderation UI
**Files:** 
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/features/movies/comment-list/comment-list.component.ts:101` — `isAdmin = computed(() => this.authService.hasRole('ROLE_ADMIN'))`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/features/movies/comment-item/comment-item.component.ts:17` — Delete button visible to admin + comment owner

**Logic:**
```typescript
// comment-item.component.ts:17
@if (isOwner() || isAdmin()) {
  <button (click)="confirmDelete()">Delete</button>
}
```

---

## 4. Admin Capabilities Comparison

### Backend ✅ IMPLEMENTED
| Capability | Backend | Frontend UI |
|-----------|---------|------------|
| Create Movie | ✅ POST `/api/movies` | ❌ No form |
| Update Movie | ✅ PUT `/api/movies/{id}` | ❌ No form |
| Delete Movie | ✅ DELETE `/api/movies/{id}` | ❌ No UI |
| Create Theater | ✅ POST `/api/theaters` | ❌ No form |
| Update Theater | ✅ PUT `/api/theaters/{id}` | ❌ No form |
| Create Showtime | ✅ POST `/api/showtimes` | ❌ No form |
| Update Showtime | ✅ PUT `/api/showtimes/{id}` | ❌ No form |
| Refund Payment | ✅ POST `/api/payments/{id}/refund` | ❌ No UI |
| Delete Comment | ✅ DELETE `/api/comments/{id}` | ✅ Button visible in comment item |
| View Theater List | ✅ GET `/api/theaters` | ✅ Read-only component |
| View Showtime List | ✅ GET `/api/showtimes` | ✅ Read-only component |

### Frontend Gap Analysis
| Gap | Impact | Priority |
|-----|--------|----------|
| No movie CRUD UI | Cannot create/edit/delete movies via UI (API works) | HIGH |
| Theater mgmt read-only | Can view but cannot create/edit | MEDIUM |
| Showtime mgmt read-only | Can view but cannot create/edit | MEDIUM |
| No payment refund UI | Cannot process refunds via UI | MEDIUM |
| No admin dashboard | No overview of admin tasks | LOW |

---

## 5. File Inventory

### Backend Controller Files with Admin Protection
```
/movie-service/src/main/java/com/namnd/movieservice/controller/
  ├── MovieController.java (3 admin endpoints)
  ├── TheaterController.java (2 admin endpoints)
  ├── ShowtimeController.java (2 admin endpoints)
  └── MovieCommentController.java (comment deletion with role check)

/payment-service/src/main/java/com/namnd/paymentservice/controller/
  └── PaymentController.java (1 admin endpoint)

/auth-service/src/main/java/com/namnd/cinema/controller/
  └── TestController.java (role-based test endpoints)
```

### Frontend Admin Module Files
```
/cinema-frontend/src/app/
├── app.routes.ts (admin route)
├── app.component.ts (admin nav in sidenav)
├── core/
│   ├── guards/admin.guard.ts
│   └── services/auth.service.ts (hasRole method)
├── shared/components/toolbar/toolbar.component.ts (admin nav link)
└── features/admin/
    ├── admin.routes.ts
    ├── theater-management/theater-management.component.ts
    └── showtime-management/showtime-management.component.ts
```

### Comments with Admin Logic
```
/cinema-frontend/src/app/features/movies/
├── comment-list/comment-list.component.ts (isAdmin computed)
└── comment-item/comment-item.component.ts (delete button visibility)
```

---

## 6. Role Hierarchy

```
ROLE_USER
  └── Basic user (comment, rate, book)

ROLE_PM
  └── Project Manager (USER + PM-level access)
  └── Eligible for @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")

ROLE_ADMIN
  └── Full privileges
  └── Can: manage catalog, refund payments, delete any comment
```

---

## 7. Security Implementation

- **JWT-based:** Roles extracted from JWT token & stored in `JwtAuthenticatedUser`
- **Method-level:** `@PreAuthorize` annotations on each admin endpoint
- **Frontend:** Guard redirects to `/movies` if not admin
- **Comment deletion:** Backend performs role check even on authenticated endpoint

---

## Unresolved Questions

1. **Movie deletion cascade:** When admin deletes a movie, are showtimes & bookings handled?
2. **Theater capacity validation:** Does theater creation validate seat counts?
3. **Payment refund webhook:** Does refund sync back to Stripe?
4. **Admin dashboard metrics:** No dashboard shows admin KPIs (total movies, bookings, revenue)?
5. **Audit logging:** Are admin actions (create/update/delete) logged?
6. **Frontend form validation:** Movie/theater/showtime create forms missing — planned or abandoned?

---

## Summary

**Backend:** Admin feature complete with 8+ protected endpoints across 3 services.
**Frontend:** Partially implemented — navigation + guards present, but CRUD UI forms missing. Admin can only view lists; full management requires direct API calls.
