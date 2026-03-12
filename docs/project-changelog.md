# Project Changelog

**Project:** ms-cinema
**Updated:** March 12, 2026

## Version 0.0.1-SNAPSHOT

### [Unreleased]

#### Features Added
- **Movie Ratings (v0.0.1)** — March 12, 2026
  - POST `/api/movies/{movieId}/ratings` - Create/update rating (1-5 stars)
  - GET `/api/movies/{movieId}/ratings` - Retrieve rating summary (average, total count, authenticated user's rating)
  - Database: `movie_ratings` table (movieId, userId, rating, createdAt, updatedAt)
  - Spring Data JPA entity with composite key (movieId, userId)

- **Movie Comments (v0.0.1)** — March 12, 2026
  - POST `/api/movies/{movieId}/comments` - Create comment with text content
  - GET `/api/movies/{movieId}/comments` - List comments paginated (20 per page, sorted by createdAt DESC)
  - PUT `/api/comments/{commentId}` - Update own comment (owner only)
  - DELETE `/api/comments/{commentId}` - Soft-delete comment (owner or ADMIN)
  - Database: `movie_comments` table (movieId, userId, content, status [ACTIVE/DELETED], createdAt, updatedAt)
  - Soft-delete via `status` column (no hard deletion)

- **Comment Reactions (v0.0.1)** — March 12, 2026
  - POST `/api/comments/{commentId}/reactions` - Toggle like/dislike on comment
  - DELETE `/api/comments/{commentId}/reactions` - Remove reaction
  - Database: `comment_reactions` table (commentId, userId, reactionType [LIKE/DISLIKE], createdAt)
  - Composite key (commentId, userId) - one reaction per user per comment

#### API Changes
- **New Endpoints (8 total):**
  - 2 rating endpoints (POST, GET)
  - 4 comment endpoints (POST, GET, PUT, DELETE)
  - 2 reaction endpoints (POST, DELETE)

- **API Gateway:**
  - Added route `/api/comments/**` → movie-service
  - Routes `/api/movies/{movieId}/comments*` to MovieCommentController
  - Routes `/api/comments/{commentId}*` to CommentReactionController

#### Database Schema
- **movie-service (moviedb):**
  - `movie_ratings` — pk: (movie_id, user_id); columns: rating, created_at, updated_at
  - `movie_comments` — pk: id; columns: movie_id, user_id, content, status, created_at, updated_at
  - `comment_reactions` — pk: (comment_id, user_id); columns: reaction_type, created_at
  - Auto-created by Hibernate ddl-auto=update

#### Security & Authorization
- **MovieRatingController:** POST requires @PreAuthorize("isAuthenticated()"), GET is public
- **MovieCommentController:**
  - POST requires authentication
  - PUT requires ownership or admin role
  - DELETE requires ownership or admin role
  - GET is public
- **CommentReactionController:** All endpoints require authentication

#### DTOs Added
- `CreateRatingRequest` (rating: Integer [1-5])
- `MovieRatingDto` (id, movieId, userId, rating, createdAt, updatedAt)
- `MovieRatingSummaryDto` (averageRating, totalRatings, userRating [nullable])
- `CreateCommentRequest` (content: String)
- `UpdateCommentRequest` (content: String)
- `MovieCommentDto` (id, movieId, userId, content, status, likeCount, dislikeCount, userReaction, createdAt, updatedAt)
- `CommentReactionRequest` (reactionType: LIKE/DISLIKE)
- `CommentReactionDto` (id, commentId, userId, reactionType, createdAt)

#### Documentation
- Updated `/docs/api-documentation.md` — Added ratings, comments, reactions endpoints table
- Updated `/docs/codebase-summary.md` — Added new models, services, controllers, 7 tables for moviedb
- Updated `/docs/system-architecture.md` — Added database schema, new features, api-gateway routes
- Updated `/docs/project-roadmap.md` — Marked Phase 3 features as complete

#### Testing
- Full integration tests covering all CRUD operations
- Authorization tests (owner/admin/public scenarios)
- Pagination tests for comment listing
- Soft-delete verification

#### Backend Implementation Details
- MovieComment deletion is soft-delete only (updates status to DELETED, preserves audit trail)
- CommentReaction is toggle-based (POST twice with same type removes, different type updates)
- Rating summary includes user's own rating only for authenticated requests
- Comments sorted by newest first (DESC by createdAt)
- Ratings use UNIQUE(movie_id, user_id) constraint for upsert pattern
- Custom repository queries: AVG() for ratings, COUNT() for like/dislike counts

#### Frontend Implementation Details
- **StarRatingComponent:** 5-star display with interactive hover/selection, read-only or editable mode
- **CommentListComponent:** Paginated comment list with load-more or infinite scroll, public visibility
- **CommentItemComponent:** Individual comment card with reactions counter, edit/delete buttons (if owner/admin)
- **MovieRatingService & MovieCommentService:** HTTP clients handling POST/GET/PUT/DELETE with pagination
- **Auth Interceptor Fix:** Now always attaches JWT token from storage when available; PUBLIC_URLS only gates 401 refresh retry (allows anonymous access)
- **MovieDetailComponent:** Integrated new components below movie details, lazy-load ratings/comments on tab change

---

## Phase 2 Releases (Historical)

**Phase 2: Microservice Integration (COMPLETE)** — December 2025 - February 2026
- 11-module Maven structure with Spring Cloud
- Eureka service discovery, Config Server, API Gateway
- JWT authentication (JJWT 0.12.6 HS512)
- movie-service, booking-service, payment-service, notification-service
- Kafka event streaming (PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent)
- OpenAPI 3.0 documentation (Swagger UI, SpringDoc 2.8.4)
- Prometheus monitoring + Grafana dashboards + Loki logging

---

## Related Documents

- [Project Roadmap](./project-roadmap.md)
- [API Documentation](./api-documentation.md)
- [System Architecture](./system-architecture.md)
- [Codebase Summary](./codebase-summary.md)
