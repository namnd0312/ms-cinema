# Phase 3: REST Controllers & Security

## Context Links

- [Plan Overview](plan.md)
- [Phase 2: DTOs & Services](phase-02-dtos-and-services.md)
- Existing controller: `movie-service/src/main/java/com/namnd/movieservice/controller/MovieController.java`
- Security config: `movie-service/src/main/java/com/namnd/movieservice/config/SecurityConfig.java`
- Auth principal: `jwt-auth-spring-boot-autoconfigure/.../JwtAuthenticatedUser.java` (record: userId, email, roles)
- userId extraction pattern: `booking-service/.../BookingController.java` lines 80-84

## Overview

- **Priority:** P1
- **Status:** pending
- **Description:** Create 3 REST controllers for ratings, comments, reactions. Update SecurityConfig to permit GET on new endpoints. Update GlobalExceptionHandler for new exception types.

## Key Insights

- Controllers are thin: extract userId from JWT principal, delegate to service
- `JwtAuthenticatedUser` record accessed via `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`
- GETs are public (permitAll); POST/PUT/DELETE require authentication
- `@PreAuthorize("isAuthenticated()")` for user-level mutations (not ADMIN-only)
- OpenAPI annotations: `@Tag`, `@Operation`, `@SecurityRequirement(name = "bearerAuth")`
- Pagination: use Spring `Pageable` with `@PageableDefault`

## Requirements

### Functional
- Rating endpoints: POST (upsert), GET summary
- Comment endpoints: POST, PUT, DELETE (own), GET paginated
- Reaction endpoints: POST toggle, DELETE remove
- All GETs public; all mutations require authenticated user

### Non-Functional
- Controllers under 80 lines each
- Consistent error responses via GlobalExceptionHandler
- Swagger docs for all endpoints

## Architecture

### Endpoint Map

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/movies/{movieId}/ratings` | USER | Create/update rating |
| GET | `/api/movies/{movieId}/ratings` | public | Get rating summary |
| POST | `/api/movies/{movieId}/comments` | USER | Create comment |
| GET | `/api/movies/{movieId}/comments` | public | List comments (paginated) |
| PUT | `/api/comments/{commentId}` | USER (owner) | Update comment |
| DELETE | `/api/comments/{commentId}` | USER (owner) | Soft-delete comment |
| POST | `/api/comments/{commentId}/reactions` | USER | Toggle like/dislike |
| DELETE | `/api/comments/{commentId}/reactions` | USER | Remove reaction |

**Note:** Comment PUT/DELETE and reaction endpoints use `/api/comments/{commentId}` (not nested under movies) since they operate on comment ID directly. This avoids redundant movieId path param.

### userId Extraction Helper

Reuse pattern from BookingController:
```java
private Long extractUserId() {
    JwtAuthenticatedUser user = (JwtAuthenticatedUser)
        SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.userId();
}
```

For GET endpoints where user may be unauthenticated, use nullable extraction:
```java
private Long extractUserIdOrNull() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser user) {
        return user.userId();
    }
    return null;
}
```

## Related Code Files

### Files to Create
- `movie-service/src/main/java/com/namnd/movieservice/controller/MovieRatingController.java`
- `movie-service/src/main/java/com/namnd/movieservice/controller/MovieCommentController.java`
- `movie-service/src/main/java/com/namnd/movieservice/controller/CommentReactionController.java`

### Files to Modify
- `movie-service/src/main/java/com/namnd/movieservice/config/SecurityConfig.java` - add GET permits for new paths
- `movie-service/src/main/java/com/namnd/movieservice/config/GlobalExceptionHandler.java` - add handlers for AccessDeniedException, EntityNotFoundException, MethodArgumentNotValidException

## Implementation Steps

### Step 1: Update SecurityConfig

Add GET permits for comments and ratings endpoints:
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.GET,
        "/api/movies/**",
        "/api/showtimes/**",
        "/api/theaters/**",
        "/api/comments/**"           // NEW: public GET on comments
    ).permitAll()
    .requestMatchers("/actuator/health", "/actuator/prometheus",
        "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
    .anyRequest().authenticated()
)
```

Note: `/api/movies/{movieId}/ratings` and `/api/movies/{movieId}/comments` GETs already covered by `/api/movies/**` wildcard.

### Step 2: Update GlobalExceptionHandler

Add specific exception handlers:
```java
@ExceptionHandler(EntityNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("status", 404, "error", "Not Found", "message", ex.getMessage()));
}

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("status", 403, "error", "Forbidden", "message", ex.getMessage()));
}

@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> e.getField() + ": " + e.getDefaultMessage())
        .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("status", 400, "error", "Bad Request", "message", message));
}
```

### Step 3: Create MovieRatingController

```java
@RestController
@RequestMapping("/api/movies/{movieId}/ratings")
@RequiredArgsConstructor
@Tag(name = "Movie Ratings", description = "Star rating endpoints")
public class MovieRatingController {

    private final MovieRatingService ratingService;

    @Operation(summary = "Rate a movie (1-5 stars, upsert)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovieRatingDto> rate(
            @PathVariable Long movieId,
            @Valid @RequestBody CreateRatingRequest request) {
        Long userId = extractUserId();
        return ResponseEntity.ok(ratingService.createOrUpdateRating(movieId, userId, request));
    }

    @Operation(summary = "Get rating summary for a movie")
    @GetMapping
    public ResponseEntity<MovieRatingSummaryDto> getSummary(@PathVariable Long movieId) {
        Long userId = extractUserIdOrNull();
        return ResponseEntity.ok(ratingService.getRatingSummary(movieId, userId));
    }

    // extractUserId() and extractUserIdOrNull() helper methods
}
```

### Step 4: Create MovieCommentController

```java
@RestController
@RequiredArgsConstructor
@Tag(name = "Movie Comments", description = "Comment endpoints")
public class MovieCommentController {

    private final MovieCommentService commentService;

    @Operation(summary = "Post a comment on a movie")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/movies/{movieId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovieCommentDto> create(
            @PathVariable Long movieId,
            @Valid @RequestBody CreateCommentRequest request) {
        Long userId = extractUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(commentService.createComment(movieId, userId, request));
    }

    @Operation(summary = "List comments for a movie (paginated)")
    @GetMapping("/api/movies/{movieId}/comments")
    public ResponseEntity<Page<MovieCommentDto>> list(
            @PathVariable Long movieId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = extractUserIdOrNull();
        return ResponseEntity.ok(commentService.getCommentsByMovie(movieId, userId, pageable));
    }

    @Operation(summary = "Update own comment")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovieCommentDto> update(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        Long userId = extractUserId();
        return ResponseEntity.ok(commentService.updateComment(commentId, userId, request));
    }

    @Operation(summary = "Delete own comment")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long commentId) {
        Long userId = extractUserId();
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    // extractUserId() and extractUserIdOrNull() helper methods
}
```

### Step 5: Create CommentReactionController

```java
@RestController
@RequestMapping("/api/comments/{commentId}/reactions")
@RequiredArgsConstructor
@Tag(name = "Comment Reactions", description = "Like/dislike endpoints")
public class CommentReactionController {

    private final CommentReactionService reactionService;

    @Operation(summary = "Toggle like/dislike on a comment")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentReactionDto> toggle(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentReactionRequest request) {
        Long userId = extractUserId();
        return ResponseEntity.ok(reactionService.toggleReaction(commentId, userId, request));
    }

    @Operation(summary = "Remove reaction from a comment")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentReactionDto> remove(@PathVariable Long commentId) {
        Long userId = extractUserId();
        return ResponseEntity.ok(reactionService.removeReaction(commentId, userId));
    }

    // extractUserId() helper method
}
```

### Step 6: Compile verification
```bash
cd movie-service && mvn clean compile
```

## Todo List

- [ ] Update `SecurityConfig` - add GET permits for `/api/comments/**`
- [ ] Update `GlobalExceptionHandler` - add EntityNotFoundException, AccessDeniedException, validation handlers
- [ ] Create `MovieRatingController` with POST/GET endpoints
- [ ] Create `MovieCommentController` with POST/GET/PUT/DELETE endpoints
- [ ] Create `CommentReactionController` with POST/DELETE endpoints
- [ ] Verify all controllers use `@PreAuthorize("isAuthenticated()")` for mutations
- [ ] Verify public GETs use nullable userId extraction for personalized responses
- [ ] Run `mvn clean compile` - verify no errors

## Success Criteria

- All endpoints return correct HTTP status codes (201 for create, 200 for ok, 204 for delete)
- `@PreAuthorize("isAuthenticated()")` on all mutation endpoints
- GETs are public and support optional user context
- Swagger annotations present on all endpoints
- GlobalExceptionHandler returns structured error responses for 400, 403, 404
- All controller files under 80 lines

## Risk Assessment

- **extractUserId duplication across 3 controllers**: acceptable for 3 controllers; extract to utility if more controllers added later
- **PUT/DELETE on `/api/comments/{id}` not under `/api/movies`**: cleaner REST design; gateway already routes `/api/movies/**`; need to verify gateway also routes `/api/comments/**`

## Security Considerations

- `isAuthenticated()` (not `hasRole('USER')`) since all authenticated users can rate/comment regardless of role
- Ownership enforcement happens in service layer (not controller) for defense-in-depth
- userId extracted server-side from JWT; never accepted as request parameter
- Content length limited by `@Size(max = 2000)` on comment DTOs
- No admin override for editing others' comments (YAGNI)

## Next Steps

- Verify API Gateway routes `/api/comments/**` to movie-service (may need gateway config update)
- Phase 4: write tests for services and controllers
