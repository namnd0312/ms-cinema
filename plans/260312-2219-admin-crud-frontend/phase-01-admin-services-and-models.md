# Phase 1: Admin Services & Models

## Context Links
- [plan.md](./plan.md)
- [movie.model.ts](../../cinema-frontend/src/app/core/models/movie.model.ts)
- [movie.service.ts](../../cinema-frontend/src/app/core/services/movie.service.ts)
- [payment.service.ts](../../cinema-frontend/src/app/core/services/payment.service.ts)

## Overview
- **Priority:** P1 (blocker for all other phases)
- **Status:** pending
- **Description:** Create missing services (TheaterService, ShowtimeAdminService), add admin request interfaces to models, update Theater model with backend fields.

## Key Insights
- MovieService already has `createMovie`, `updateMovie`, `deleteMovie` — no changes needed
- PaymentService already has `refundPayment` — only need `getAllPayments` (backend gap)
- TheaterManagementComponent uses raw HttpClient — needs proper service
- Backend TheaterDto has `location`, `totalRows`, `totalColumns`, `createdAt` — frontend Theater model only has `id`, `name`, `totalSeats`
- Backend showtime create/update endpoints exist but no frontend service methods

## Requirements

### Functional
- TheaterService with getAll, getById, create, update
- ShowtimeAdminService with create, update (reads via MovieService.getShowtimes)
- Admin request interfaces matching backend DTOs exactly
- Updated Theater model matching TheaterDto

### Non-functional
- Services follow existing pattern: `inject(HttpClient)`, `providedIn: 'root'`
- Each service file < 200 LOC

## Architecture

```
core/
  models/
    movie.model.ts        # UPDATE: Theater fields + admin request interfaces
  services/
    theater.service.ts    # NEW: theater CRUD
    showtime-admin.service.ts  # NEW: showtime create/update
    movie.service.ts      # NO CHANGE (already has CRUD)
    payment.service.ts    # UPDATE: add getAllPayments (pending backend)
```

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/core/services/theater.service.ts`
- `cinema-frontend/src/app/core/services/showtime-admin.service.ts`

### Files to Modify
- `cinema-frontend/src/app/core/models/movie.model.ts`
- `cinema-frontend/src/app/core/services/payment.service.ts`

## Implementation Steps

### Step 1: Update Theater model in movie.model.ts

Add missing backend fields and admin request interfaces:

```typescript
// UPDATE existing Theater interface
export interface Theater {
  id: number;
  name: string;
  location: string;
  totalRows: number;
  totalColumns: number;
  totalSeats: number;   // keep for backward compat
  createdAt: string;
}

// ADD admin request interfaces
export interface CreateMovieRequest {
  title: string;
  description: string;
  genre: string;
  durationMin: number;
  rating: string;
  posterUrl: string;
  releaseDate: string;  // ISO date string
}

export interface CreateTheaterRequest {
  name: string;
  location: string;
  totalRows: number;
  totalColumns: number;
}

export interface CreateShowtimeRequest {
  movieId: number;
  theaterId: number;
  startTime: string;    // ISO datetime string
  endTime: string;
  basePrice: number;
}
```

### Step 2: Create TheaterService

```typescript
// cinema-frontend/src/app/core/services/theater.service.ts
@Injectable({ providedIn: 'root' })
export class TheaterService {
  private http = inject(HttpClient);

  getTheaters(): Observable<Theater[]> {
    return this.http.get<Theater[]>('/api/theaters');
  }

  getTheater(id: number): Observable<Theater> {
    return this.http.get<Theater>(`/api/theaters/${id}`);
  }

  createTheater(request: CreateTheaterRequest): Observable<Theater> {
    return this.http.post<Theater>('/api/theaters', request);
  }

  updateTheater(id: number, request: CreateTheaterRequest): Observable<Theater> {
    return this.http.put<Theater>(`/api/theaters/${id}`, request);
  }
}
```

### Step 3: Create ShowtimeAdminService

```typescript
// cinema-frontend/src/app/core/services/showtime-admin.service.ts
@Injectable({ providedIn: 'root' })
export class ShowtimeAdminService {
  private http = inject(HttpClient);

  createShowtime(request: CreateShowtimeRequest): Observable<Showtime> {
    return this.http.post<Showtime>('/api/showtimes', request);
  }

  updateShowtime(id: number, request: CreateShowtimeRequest): Observable<Showtime> {
    return this.http.put<Showtime>(`/api/showtimes/${id}`, request);
  }
}
```

### Step 4: Add getAllPayments to PaymentService

```typescript
// Add to payment.service.ts
getAllPayments(): Observable<Payment[]> {
  return this.http.get<Payment[]>('/api/payments');
}
```

**NOTE:** Backend endpoint `GET /api/payments` does NOT exist yet. This method will 404 until backend is updated. Flag in Phase 5.

## Todo List
- [ ] Update Theater interface in movie.model.ts (add location, totalRows, totalColumns, createdAt)
- [ ] Add CreateMovieRequest, CreateTheaterRequest, CreateShowtimeRequest interfaces
- [ ] Create theater.service.ts
- [ ] Create showtime-admin.service.ts
- [ ] Add getAllPayments to payment.service.ts
- [ ] Verify compile: `ng build` or `ng serve`

## Success Criteria
- All new services compile without errors
- Theater model includes all backend TheaterDto fields
- Request interfaces match backend @RequestBody DTOs exactly
- Existing components still work (Theater backward compat with totalSeats)

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Theater model change breaks existing usage | Medium | Keep `totalSeats` field for backward compat |
| getAllPayments 404 | Low | Known gap, handle in Phase 5 |

## Security Considerations
- All admin endpoints protected by `@PreAuthorize("hasRole('ADMIN')")` on backend
- Frontend adminGuard prevents non-admin route access
- JWT token automatically attached by HttpClient interceptor
