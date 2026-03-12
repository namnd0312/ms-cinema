# Explore: Admin Frontend Code Documentation

**Date:** 2026-03-12  
**Scope:** cinema-frontend admin module structure  
**Thoroughness:** Medium  

---

## Current Admin Routes

**File:** `/src/app/features/admin/admin.routes.ts`

Routes defined:
- **`/admin`** - Root admin path (protected by `adminGuard`)
  - **`/admin/theaters`** - Theater management component (lazy-loaded)
  - **`/admin/showtimes`** - Showtime management component (lazy-loaded)
  - Default redirect: `/admin` → `/admin/theaters`

Security: All routes protected by `adminGuard` (checks authentication + `ROLE_ADMIN` role)

---

## Admin Components

### 1. TheaterManagementComponent
**File:** `/src/app/features/admin/theater-management/theater-management.component.ts`

**Current Functionality:**
- Fetches theaters from `/api/theaters`
- Displays list in Material cards
- Shows theater name and total seats
- Loading spinner during fetch
- Empty state handling

**Capabilities:**
- Read-only display only
- Uses HttpClient directly (no service layer)
- Inline template with basic styling

**Missing:** Create, update, delete operations; form handling; error messages

### 2. ShowtimeManagementComponent
**File:** `/src/app/features/admin/showtime-management/showtime-management.component.ts`

**Current Functionality:**
- Fetches showtimes from `MovieService.getShowtimes()`
- Displays list in Material cards
- Shows: movie title, theater name, start time (formatted), base price
- Loading spinner during fetch
- Empty state handling

**Capabilities:**
- Read-only display only
- Uses MovieService (good pattern)
- Inline template with basic styling

**Missing:** Create, update, delete operations; filtering; form handling

---

## Security & Authentication

**File:** `/src/app/core/guards/admin.guard.ts`

Guard implementation:
```typescript
- Checks: authService.isAuthenticated() && authService.hasRole('ROLE_ADMIN')
- Redirect fallback: navigates to /movies if unauthorized
- Type: CanActivateFn (new Angular syntax)
```

Status: **Functional and secure**

---

## Existing Services

### MovieService
**File:** `/src/app/core/services/movie.service.ts`

**Available Methods:**
- `getMovies()` - fetch all movies
- `getMovie(id)` - fetch single movie
- `createMovie(movie)` - create new movie (POST)
- `updateMovie(id, movie)` - update movie (PUT)
- `deleteMovie(id)` - delete movie (DELETE)
- `getShowtimes(movieId?)` - fetch all/filtered showtimes
- `getShowtime(id)` - fetch single showtime
- `getShowtimeSeats(showtimeId)` - fetch seats for showtime

**Pattern:** Uses injectable HttpClient, returns Observables

**Status:** Movie operations fully CRUD; Showtime operations partially CRUD (missing create/update/delete)

### Other Services
- `AuthService` - authentication/role checking ✓
- `BookingService` - booking operations
- `PaymentService` - payment operations
- `MovieRatingService` - rating operations
- `MovieCommentService` - comment operations
- `LoadingService` - global loading state

**Status:** No dedicated theater or showtime service exists; operations done directly via HttpClient or MovieService

---

## Existing Models

**File:** `/src/app/core/models/movie.model.ts`

**Defined Interfaces:**

### Movie
```typescript
- id, title, description, durationMinutes
- genre, releaseDate, posterUrl
- rating, averageRating, totalRatings, commentCount
```

### Theater
```typescript
- id, name, totalSeats
```

### Seat
```typescript
- id, seatLabel, seatType, rowNumber, columnNumber
- price, status (AVAILABLE | OCCUPIED | RESERVED)
```

### Showtime
```typescript
- id, movie (Movie), theater (Theater)
- startTime, endTime, basePrice
```

**Status:** Core models defined; missing admin-specific DTOs for create/update operations

---

## Admin Navigation Integration

**Status:** Not found in app.component.ts or main toolbar.
- No admin link in navigation visible
- Access via direct URL or admin guard enforcement only
- Consider: Add admin nav link for ROLE_ADMIN users

---

## Architecture Observations

**Strengths:**
- Lazy-loaded admin routes (performance)
- Guard protection on all admin routes
- Signal-based state management (Angular best practice)
- Material Design components used
- Separation of concerns with services

**Gaps:**
- **No dedicated theater/showtime services** - components use HttpClient directly or MovieService
- **Read-only UI** - no create/update/delete forms
- **Missing error handling** - no error messages displayed
- **No validation** - no form validation for submissions
- **No confirmation dialogs** - risky for destructive actions
- **Limited search/filter** - no list filtering or pagination
- **Admin navigation** - no admin link in main nav

---

## Implementation Checklist

### What Exists ✓
- Admin routes structure
- Theater list display
- Showtime list display
- Admin guard (authentication/authorization)
- Core models (Movie, Theater, Seat, Showtime)
- Basic CRUD for movies (service)
- Basic services structure

### What's Missing ✗
- Theater CRUD forms (create, update, delete)
- Showtime CRUD forms (create, update, delete)
- Dedicated theater service
- Dedicated showtime service
- Error handling & user feedback
- Form validation
- Confirmation dialogs
- Search/filter functionality
- List pagination
- Admin navigation in main app
- Seat management component
- Movie management component (admin version)

---

## Unresolved Questions

1. Should theater/showtime services be created, or continue using HttpClient directly?
2. What backend endpoints exist for theater CRUD operations?
3. Should showtime creation/update support seat management UI?
4. What pagination/sorting requirements for admin lists?
5. Error message UX preferences (toasts, snackbars, inline)?
