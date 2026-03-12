# Phase 2: Star Rating Component

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1 — Services & Models](./phase-01-services-and-models.md)
- Movie detail page: `cinema-frontend/src/app/features/movies/movie-detail/movie-detail.component.ts`
- Existing star display in movie detail: uses `mat-icon star` with `#ffc107` color

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Create a reusable star-rating component (1-5 stars) and integrate it into the movie detail page. Shows average rating + total count, allows authenticated users to submit/update their rating.

## Key Insights
- Existing movie detail already shows `rating/10` with a single star icon — replace with new component
- Backend rating is 1-5, existing `Movie.rating` was out of 10 — new `averageRating` is 1-5 scale
- `MovieRatingSummaryDto.userRating` is null when not logged in or hasn't rated
- POST to `/api/movies/{id}/ratings` creates or updates (idempotent)
- Use filled/half/empty star icons from Material Icons (`star`, `star_half`, `star_border`)

## Requirements
- **Functional:**
  - Display average rating as 5 filled/empty stars + numeric value + total count
  - Logged-in users: hover shows preview, click submits rating
  - Not logged in: stars are read-only, no hover effect
  - After rating, update display immediately (optimistic or refetch summary)
- **Non-functional:**
  - Reusable standalone component with inputs for display-only mode
  - Mobile-friendly tap targets (min 40px per star)

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/features/movies/star-rating/star-rating.component.ts`

### Files to Modify
- `cinema-frontend/src/app/features/movies/movie-detail/movie-detail.component.ts` — replace static rating display, add rating fetch + submit logic

## Architecture

```
StarRatingComponent (standalone)
  Inputs:
    - averageRating: number (0-5)
    - totalRatings: number
    - userRating: number | null
    - readonly: boolean (default false)
  Outputs:
    - ratingSelected: EventEmitter<number> (1-5)

MovieDetailComponent
  - Fetches MovieRatingSummaryDto on init
  - Passes data to StarRatingComponent
  - Handles ratingSelected event → calls MovieRatingService.rateMovie()
  - Refreshes summary after rating
```

## Implementation Steps

### Step 1: Create `star-rating.component.ts`

Standalone component with inline template/styles (matching codebase convention).

**Template logic:**
- Loop 1-5 stars
- Each star: filled if `i <= displayRating`, half if `i - 0.5 <= displayRating < i`, empty otherwise
- `displayRating` = hoverRating (on mouseover) or averageRating (default)
- On `mouseenter` star: set hoverRating = i (only if not readonly)
- On `mouseleave` container: reset hoverRating
- On `click` star: emit `ratingSelected(i)` (only if not readonly)
- Show user's existing rating with a highlight or text indicator ("Your rating: X")
- Display: `averageRating` numeric + `(totalRatings reviews)`

**Styles:**
- Stars: `color: #ffc107` (filled), `color: rgba(255,255,255,0.3)` (empty)
- Cursor: pointer when interactive, default when readonly
- Star size: 28px, hover scale effect
- Container: `display: inline-flex; align-items: center; gap: 4px`

**Component class:**
```typescript
@Component({ selector: 'app-star-rating', standalone: true, imports: [MatIconModule] })
export class StarRatingComponent {
  averageRating = input<number>(0);
  totalRatings = input<number>(0);
  userRating = input<number | null>(null);
  readonly = input<boolean>(false);
  ratingSelected = output<number>();

  hoverRating = signal<number>(0);

  getStarIcon(index: number): string {
    const rating = this.hoverRating() || this.averageRating();
    if (index <= rating) return 'star';
    if (index - 0.5 <= rating) return 'star_half';
    return 'star_border';
  }

  onStarHover(index: number): void { if (!this.readonly()) this.hoverRating.set(index); }
  onStarLeave(): void { this.hoverRating.set(0); }
  onStarClick(index: number): void { if (!this.readonly()) this.ratingSelected.emit(index); }
}
```

### Step 2: Integrate into `MovieDetailComponent`

1. Add imports: `StarRatingComponent`, `MovieRatingService`, `AuthService`
2. Add signals: `ratingSummary`, `isAuthenticated`
3. On `ngOnInit` after movie loads: call `movieRatingService.getRatingSummary(movieId)`
4. Replace existing rating display `<span class="rating">...` with:
```html
<app-star-rating
  [averageRating]="ratingSummary()?.averageRating ?? 0"
  [totalRatings]="ratingSummary()?.totalRatings ?? 0"
  [userRating]="ratingSummary()?.userRating ?? null"
  [readonly]="!isAuthenticated()"
  (ratingSelected)="onRate($event)"
/>
```
5. Add `onRate(rating: number)` method that calls service then refreshes summary
6. Show MatSnackBar on successful rating ("Rating submitted!")

### Step 3: Handle user rating indicator

When `userRating` is set, show small text below stars: "You rated X/5" — implemented inside `StarRatingComponent`.

## Todo List
- [ ] Create `star-rating.component.ts` with inline template/styles
- [ ] Implement star display logic (filled/half/empty)
- [ ] Implement hover preview for interactive mode
- [ ] Implement click-to-rate with event emission
- [ ] Show "You rated X/5" when userRating is set
- [ ] Integrate into `movie-detail.component.ts`
- [ ] Fetch rating summary on movie detail load
- [ ] Handle rating submission + refresh
- [ ] Test: readonly mode for unauthenticated users
- [ ] Test: rating submission for authenticated users
- [ ] Compile check

## Success Criteria
- Average rating displays correctly with star icons
- Hover preview works for logged-in users only
- Click submits rating, display updates immediately
- Read-only mode for anonymous users (no hover/click)
- Mobile: stars are tappable with adequate touch targets

## Risk Assessment
- **Auth interceptor issue:** GET `/api/movies/{id}/ratings` might not send JWT because `/api/movies` is in PUBLIC_URLS — `userRating` would always be null for logged-in users. **Mitigation:** Fix in Phase 1 — make PUBLIC_URLS matching more specific.

## Security Considerations
- Rating values clamped 1-5 on backend; frontend should also validate before sending
- Only authenticated users can rate (hide interactive UI, backend rejects anyway)
