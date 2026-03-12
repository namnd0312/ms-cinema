# Phase 2: DTOs & Service Layer

## Context Links

- [Plan Overview](plan.md)
- [Phase 1: Entities](phase-01-entities-and-repositories.md)
- Existing service pattern: `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieServiceImpl.java`
- Existing DTO pattern: `movie-service/src/main/java/com/namnd/movieservice/dto/MovieDto.java`

## Overview

- **Priority:** P1 (blocking for Phase 3 controllers)
- **Status:** pending
- **Description:** Create request/response DTOs (records) and service Interface+Impl for ratings, comments, and comment reactions.

## Key Insights

- DTOs are Java records; request records use Jakarta Validation annotations
- Services use Interface + Impl pattern with `@RequiredArgsConstructor`
- Static converter methods in ServiceImpl (e.g., `toDto()`)
- `@Transactional` on all write operations
- `EntityNotFoundException` for missing records
- userId extracted from `JwtAuthenticatedUser` in controller, passed as Long to service

## Requirements

### Functional
- **RatingService:** upsert rating (create or update), get user's rating for movie, get movie avg + count
- **CommentService:** create, update (own only), soft-delete (own only), list by movie (paginated, ACTIVE only)
- **CommentReactionService:** toggle reaction (like/dislike/remove), get counts per comment

### Non-Functional
- Each file under 200 lines
- No business logic in controllers; all in service layer
- Ownership checks (userId match) in service, not controller

## Architecture

```
Controller --> Service Interface --> ServiceImpl --> Repository
                                         |
                                    DTO conversion (static toDto methods)
```

### DTO Design

| DTO | Type | Fields |
|-----|------|--------|
| `CreateRatingRequest` | request record | `@Min(1) @Max(5) Integer rating` |
| `MovieRatingDto` | response record | `Long id, Long movieId, Long userId, Integer rating, LocalDateTime createdAt` |
| `MovieRatingSummaryDto` | response record | `Double averageRating, Long totalRatings, Integer userRating` |
| `CreateCommentRequest` | request record | `@NotBlank @Size(max=2000) String content` |
| `UpdateCommentRequest` | request record | `@NotBlank @Size(max=2000) String content` |
| `MovieCommentDto` | response record | `Long id, Long movieId, Long userId, String content, Long likeCount, Long dislikeCount, String userReaction, LocalDateTime createdAt, LocalDateTime updatedAt` |
| `CommentReactionRequest` | request record | `@NotNull Boolean isLike` |
| `CommentReactionDto` | response record | `Long commentId, Long likeCount, Long dislikeCount, String userReaction` |

## Related Code Files

### Files to Create
- `movie-service/src/main/java/com/namnd/movieservice/dto/CreateRatingRequest.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/MovieRatingDto.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/MovieRatingSummaryDto.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/CreateCommentRequest.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/UpdateCommentRequest.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/MovieCommentDto.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/CommentReactionRequest.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/CommentReactionDto.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/MovieRatingService.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/MovieCommentService.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/CommentReactionService.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieRatingServiceImpl.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieCommentServiceImpl.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/CommentReactionServiceImpl.java`

### Files to Modify
- `movie-service/src/main/java/com/namnd/movieservice/dto/MovieDto.java` - add `averageRating`, `totalRatings`, `commentCount`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieServiceImpl.java` - update `toDto()` to include rating/comment aggregations

## Implementation Steps

### Step 1: Create request DTOs

**CreateRatingRequest.java:**
```java
public record CreateRatingRequest(
    @NotNull @Min(1) @Max(5) Integer rating
) {}
```

**CreateCommentRequest.java:**
```java
public record CreateCommentRequest(
    @NotBlank @Size(max = 2000) String content
) {}
```

**UpdateCommentRequest.java:**
```java
public record UpdateCommentRequest(
    @NotBlank @Size(max = 2000) String content
) {}
```

**CommentReactionRequest.java:**
```java
public record CommentReactionRequest(
    @NotNull Boolean isLike
) {}
```

### Step 2: Create response DTOs

**MovieRatingDto.java:**
```java
public record MovieRatingDto(
    Long id, Long movieId, Long userId, Integer rating,
    LocalDateTime createdAt
) {}
```

**MovieRatingSummaryDto.java:**
```java
public record MovieRatingSummaryDto(
    Double averageRating, Long totalRatings, Integer userRating
) {}
```
- `userRating` is null for unauthenticated users

**MovieCommentDto.java:**
```java
public record MovieCommentDto(
    Long id, Long movieId, Long userId, String content,
    Long likeCount, Long dislikeCount, String userReaction,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}
```
- `userReaction`: "LIKE", "DISLIKE", or null (for unauth/no reaction)
- `likeCount`/`dislikeCount` aggregated from CommentReaction

**CommentReactionDto.java:**
```java
public record CommentReactionDto(
    Long commentId, Long likeCount, Long dislikeCount, String userReaction
) {}
```

### Step 3: Update MovieDto
Add 3 fields to existing record:
```java
public record MovieDto(
    Long id, String title, String description, String genre,
    Integer durationMin, String rating, String posterUrl,
    LocalDate releaseDate, String status, LocalDateTime createdAt,
    Double averageRating, Long totalRatings, Long commentCount  // NEW
) {}
```

### Step 4: Create MovieRatingService interface + impl

**Interface:**
```java
public interface MovieRatingService {
    MovieRatingDto createOrUpdateRating(Long movieId, Long userId, CreateRatingRequest request);
    MovieRatingSummaryDto getRatingSummary(Long movieId, Long userId);
}
```

**Impl key logic:**
- `createOrUpdateRating`: find existing by (movieId, userId); if exists update rating, else create new. Use `@Transactional`.
- `getRatingSummary`: query avg + count + optional user rating. `userId` can be null (unauth).

### Step 5: Create MovieCommentService interface + impl

**Interface:**
```java
public interface MovieCommentService {
    MovieCommentDto createComment(Long movieId, Long userId, CreateCommentRequest request);
    MovieCommentDto updateComment(Long commentId, Long userId, UpdateCommentRequest request);
    void deleteComment(Long commentId, Long userId);
    Page<MovieCommentDto> getCommentsByMovie(Long movieId, Long userId, Pageable pageable);
}
```

**Impl key logic:**
- `createComment`: create new MovieComment with ACTIVE status
- `updateComment`: find by id, verify `userId` matches entity userId, update content. Throw `AccessDeniedException` if not owner.
- `deleteComment`: find by id, verify ownership, set status=DELETED (soft delete)
- `getCommentsByMovie`: paginated query for ACTIVE comments. Enrich each with like/dislike counts and user's reaction (if userId non-null).
- `toDto()` static method enriches with reaction counts from `CommentReactionRepository`

### Step 6: Create CommentReactionService interface + impl

**Interface:**
```java
public interface CommentReactionService {
    CommentReactionDto toggleReaction(Long commentId, Long userId, CommentReactionRequest request);
    CommentReactionDto removeReaction(Long commentId, Long userId);
}
```

**Impl key logic:**
- `toggleReaction`: find existing by (commentId, userId).
  - If no existing: create new reaction
  - If existing with same isLike: delete it (toggle off)
  - If existing with different isLike: update it (switch)
- `removeReaction`: find and delete. Return updated counts.
- Both return `CommentReactionDto` with refreshed counts

### Step 7: Update MovieServiceImpl.toDto()
Inject `MovieRatingRepository` and `MovieCommentRepository` into MovieServiceImpl. Update `toDto()` to a non-static method that queries aggregations:
```java
// Change from static to instance method
private MovieDto toDto(Movie m) {
    Double avg = movieRatingRepository.findAverageRatingByMovieId(m.getId());
    Long totalRatings = movieRatingRepository.countByMovieId(m.getId());
    Long commentCount = movieCommentRepository.countByMovieIdAndStatus(m.getId(), CommentStatus.ACTIVE);
    return new MovieDto(m.getId(), m.getTitle(), ... , avg, totalRatings, commentCount);
}
```

**Performance note:** For `findAll()`, this creates N+2 queries per movie. Acceptable at current scale. If performance degrades, optimize with batch queries or denormalized columns later (YAGNI).

### Step 8: Compile verification
```bash
cd movie-service && mvn clean compile
```

## Todo List

- [ ] Create `CreateRatingRequest` record with validation
- [ ] Create `MovieRatingDto` response record
- [ ] Create `MovieRatingSummaryDto` response record
- [ ] Create `CreateCommentRequest` record with validation
- [ ] Create `UpdateCommentRequest` record with validation
- [ ] Create `MovieCommentDto` response record
- [ ] Create `CommentReactionRequest` record with validation
- [ ] Create `CommentReactionDto` response record
- [ ] Update `MovieDto` with 3 new aggregate fields
- [ ] Create `MovieRatingService` interface
- [ ] Create `MovieRatingServiceImpl` with upsert logic
- [ ] Create `MovieCommentService` interface
- [ ] Create `MovieCommentServiceImpl` with ownership checks
- [ ] Create `CommentReactionService` interface
- [ ] Create `CommentReactionServiceImpl` with toggle logic
- [ ] Update `MovieServiceImpl` toDto to include aggregations
- [ ] Run `mvn clean compile` - verify no errors

## Success Criteria

- All DTOs use record pattern with Jakarta Validation
- Services follow Interface+Impl pattern with `@RequiredArgsConstructor`
- Ownership enforcement in comment update/delete (throws AccessDeniedException)
- Rating upsert logic handles create + update in single method
- Reaction toggle handles 3 cases: create, switch, remove
- MovieDto includes aggregated rating/comment data
- All files under 200 lines

## Risk Assessment

- **N+1 in MovieServiceImpl.findAll()**: accepted for now; optimize if >100 movies returned
- **Race condition on upsert**: DB unique constraint prevents duplicate; `@Transactional` ensures atomicity
- **toDto becoming non-static**: requires injecting repos into MovieServiceImpl; minor refactor

## Security Considerations

- Ownership check: only comment author can edit/delete (userId comparison in service)
- userId comes from JWT principal (trusted); never from request body
- Rating value validated by `@Min(1) @Max(5)` on DTO
- Comment content size limited by `@Size(max = 2000)`

## Next Steps

- Phase 3 uses service interfaces to wire controllers
- Controllers extract userId from `JwtAuthenticatedUser` and pass to service methods
