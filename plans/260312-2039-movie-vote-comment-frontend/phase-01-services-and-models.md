# Phase 1: TypeScript Models & Angular Services

## Context Links
- [Plan Overview](./plan.md)
- Existing service pattern: `cinema-frontend/src/app/core/services/movie.service.ts`
- Existing model pattern: `cinema-frontend/src/app/core/models/movie.model.ts`
- Auth interceptor: `cinema-frontend/src/app/core/interceptors/auth.interceptor.ts`

## Overview
- **Priority:** P1 — foundation for all subsequent phases
- **Status:** pending
- **Description:** Create TypeScript interfaces matching backend DTOs and Angular services for ratings, comments, and reactions API calls. Update auth interceptor to allow public GET requests.

## Key Insights
- Services follow `inject(HttpClient)` + `@Injectable({ providedIn: 'root' })` pattern
- Auth interceptor has a `PUBLIC_URLS` array — GET endpoints for ratings/comments must be added
- Existing `Movie` interface needs `averageRating`, `totalRatings`, `commentCount` fields
- Backend uses Spring `Page<T>` pagination — need a generic `Page<T>` interface

## Requirements
- **Functional:** Interfaces for all DTOs, services for all endpoints, public access for GET endpoints
- **Non-functional:** Follow existing patterns exactly, keep files under 200 lines

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/core/models/movie-rating.model.ts`
- `cinema-frontend/src/app/core/models/movie-comment.model.ts`
- `cinema-frontend/src/app/core/services/movie-rating.service.ts`
- `cinema-frontend/src/app/core/services/movie-comment.service.ts`

### Files to Modify
- `cinema-frontend/src/app/core/models/movie.model.ts` — add `averageRating`, `totalRatings`, `commentCount`
- `cinema-frontend/src/app/core/interceptors/auth.interceptor.ts` — add rating/comment GET paths to PUBLIC_URLS

## Implementation Steps

### Step 1: Update `Movie` interface
Add to `movie.model.ts`:
```typescript
averageRating: number;
totalRatings: number;
commentCount: number;
```

### Step 2: Create `movie-rating.model.ts`
```typescript
export interface MovieRatingDto {
  id: number;
  movieId: number;
  userId: number;
  rating: number;
  createdAt: string;
}

export interface MovieRatingSummaryDto {
  averageRating: number;
  totalRatings: number;
  userRating: number | null;
}

export interface CreateRatingRequest {
  rating: number; // 1-5
}
```

### Step 3: Create `movie-comment.model.ts`
```typescript
export interface MovieCommentDto {
  id: number;
  movieId: number;
  userId: number;
  content: string;
  likeCount: number;
  dislikeCount: number;
  userReaction: 'LIKE' | 'DISLIKE' | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCommentRequest {
  content: string; // max 2000 chars
}

export interface UpdateCommentRequest {
  content: string; // max 2000 chars
}

export interface CommentReactionDto {
  commentId: number;
  likeCount: number;
  dislikeCount: number;
  userReaction: 'LIKE' | 'DISLIKE' | null;
}

export interface CommentReactionRequest {
  isLike: boolean;
}

// Spring Page<T> shape
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number; // current page (0-indexed)
  first: boolean;
  last: boolean;
}
```

### Step 4: Create `movie-rating.service.ts`
```typescript
@Injectable({ providedIn: 'root' })
export class MovieRatingService {
  private http = inject(HttpClient);

  getRatingSummary(movieId: number): Observable<MovieRatingSummaryDto>
  rateMovie(movieId: number, request: CreateRatingRequest): Observable<MovieRatingDto>
}
```

### Step 5: Create `movie-comment.service.ts`
```typescript
@Injectable({ providedIn: 'root' })
export class MovieCommentService {
  private http = inject(HttpClient);

  getComments(movieId: number, page: number, size: number): Observable<Page<MovieCommentDto>>
  createComment(movieId: number, request: CreateCommentRequest): Observable<MovieCommentDto>
  updateComment(commentId: number, request: UpdateCommentRequest): Observable<MovieCommentDto>
  deleteComment(commentId: number): Observable<void>
  reactToComment(commentId: number, request: CommentReactionRequest): Observable<CommentReactionDto>
  removeReaction(commentId: number): Observable<CommentReactionDto>
}
```

### Step 6: Update auth interceptor PUBLIC_URLS
Add these entries so unauthenticated users can view ratings and comments:
```typescript
'/api/movies/',  // catches /api/movies/{id}/ratings and /api/movies/{id}/comments
```
Note: The existing `/api/movies` entry already matches GET movie list. But the interceptor uses `.includes()` so `/api/movies` already covers `/api/movies/{id}/ratings`. Verify this — may need no change. The interceptor attaches token when available regardless; it only *skips* for public URLs. Actually, on re-read: public URLs skip token attachment entirely. This is wrong for authenticated users viewing ratings (need token for `userRating`). **Better approach:** Do NOT add to PUBLIC_URLS. The interceptor should attach token when present and skip when absent. The current logic only attaches if token exists (`const authReq = token ? addToken(req, token) : req`), which is already correct. No interceptor changes needed.

## Todo List
- [ ] Update `Movie` interface with new fields
- [ ] Create `movie-rating.model.ts`
- [ ] Create `movie-comment.model.ts` (includes `Page<T>`)
- [ ] Create `movie-rating.service.ts`
- [ ] Create `movie-comment.service.ts`
- [ ] Verify auth interceptor handles mixed public/auth requests correctly (likely no changes needed)
- [ ] Compile check — run `ng build` or `ng serve`

## Success Criteria
- All interfaces match backend DTOs exactly
- Services compile with no errors
- GET endpoints work without auth, POST/PUT/DELETE attach JWT automatically

## Risk Assessment
- **Auth interceptor PUBLIC_URLS:** The existing `/api/movies` entry already matches all `/api/movies/*` paths via `.includes()`. This means GET ratings/comments won't have tokens attached for logged-in users. **Mitigation:** Need to make PUBLIC_URLS more specific (exact match) or change logic to always attach token when available. Recommended: always attach token if present, only skip refresh logic for public URLs.
