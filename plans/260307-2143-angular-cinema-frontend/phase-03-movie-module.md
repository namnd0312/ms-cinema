# Phase 03: Movie Module

## Context Links
- [Cinema UI Patterns](./research/researcher-02-cinema-ui-patterns.md) — card grid, filtering
- [Phase 01](./phase-01-project-setup.md) — shared models
- Backend: `GET /api/movies`, `GET /api/movies/{id}`, `GET /api/showtimes`

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Movie browsing with card grid, detail page, and showtime selection. Public-facing (no auth required for browsing).

## Key Insights
- Responsive card grid with `auto-fill, minmax(220px, 1fr)`
- Skeleton loaders during data fetch improve perceived performance
- Showtime grouped by date for easier selection
- Debounced search (300ms) for title filtering

## Requirements
### Functional
- Movie list page with card grid (poster, title, genre, rating)
- Search/filter by title, genre
- Movie detail page (full info + showtimes)
- Showtime selection → navigate to booking
- Admin: create/edit/delete movies (basic forms)

### Non-functional
- Lazy-loaded feature routes
- Responsive grid (4 cols desktop → 2 cols tablet → 1 col mobile)
- Skeleton loading states

## Architecture
```
features/movies/
├── movies.routes.ts
├── movie-list/
│   └── movie-list.component.ts       # Card grid + search
├── movie-detail/
│   └── movie-detail.component.ts     # Details + showtimes
├── movie-card/
│   └── movie-card.component.ts       # Reusable card
└── movie-admin/
    └── movie-form.component.ts       # Create/edit form (admin)

core/services/
└── movie.service.ts
```

## Related Code Files
- **Create:** `core/services/movie.service.ts`
- **Create:** `features/movies/movies.routes.ts`
- **Create:** `features/movies/movie-list/movie-list.component.ts`
- **Create:** `features/movies/movie-detail/movie-detail.component.ts`
- **Create:** `features/movies/movie-card/movie-card.component.ts`
- **Create:** `features/movies/movie-admin/movie-form.component.ts`
- **Modify:** `app.routes.ts` — add movies lazy route

## Implementation Steps
1. Create `MovieService`:
   - `getMovies(): Observable<Movie[]>`
   - `getMovie(id): Observable<Movie>`
   - `getShowtimes(movieId?): Observable<Showtime[]>`
   - `getShowtimeSeats(showtimeId): Observable<Seat[]>`
   - Admin: `createMovie()`, `updateMovie()`, `deleteMovie()`
2. Create `MovieCardComponent` (standalone, reusable):
   - Input: `movie: Movie`
   - Display: poster image, title, genre chips, rating stars
   - Output: `selectMovie` event
   - Hover effect: scale 1.03 + elevation change
3. Create `MovieListComponent`:
   - Fetch movies on init via `MovieService`
   - Search input with `debounceTime(300)` filtering
   - Genre filter (MatSelect or chips)
   - Display cards in responsive CSS Grid
   - `@for (movie of filteredMovies(); track movie.id)` loop
   - `@if (loading())` show skeleton cards
4. Create `MovieDetailComponent`:
   - Route param `:id` → fetch movie details
   - Show poster, title, description, genre, duration, rating
   - List showtimes grouped by date
   - Each showtime: time, theater name, available seats count
   - "Book Now" button per showtime → navigate to `/booking/:showtimeId`
5. Create `MovieFormComponent` (admin):
   - Reactive form for create/edit
   - Fields: title, description, genre, duration, rating, posterUrl
   - Guard with `adminGuard` (check ROLE_ADMIN)
6. Define routes in `movies.routes.ts`:
   - `''` → MovieListComponent
   - `:id` → MovieDetailComponent
   - `admin/new` → MovieFormComponent (admin guard)
   - `admin/:id/edit` → MovieFormComponent (admin guard)

## Todo List
- [ ] MovieService with API calls
- [ ] MovieCardComponent (reusable)
- [ ] MovieListComponent with search/filter
- [ ] MovieDetailComponent with showtimes
- [ ] MovieFormComponent (admin CRUD)
- [ ] Movies routes with lazy loading
- [ ] Responsive grid styling
- [ ] Skeleton loading states

## Success Criteria
- Movies load and display in responsive card grid
- Search filters movies by title (debounced)
- Movie detail shows grouped showtimes
- "Book Now" navigates to booking with showtime ID
- Admin can create/edit movies

## Risk Assessment
- No poster images available — use placeholder image fallback
- Large movie lists — pagination not in backend API; client-side filtering sufficient for MVP

## Security Considerations
- Admin routes guarded by role check
- Movie browsing is public (no auth required)

## Next Steps
- Phase 04: Booking Module (receives showtimeId from movie detail)
