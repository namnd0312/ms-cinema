# Phase Implementation Report

## Executed Phase
- Phase: Phases 1-3 (Entities, Repositories, DTOs, Services, Controllers, Security)
- Plan: plans/260312-1954-movie-vote-comment/
- Status: completed

## Files Modified

### New files created
- `model/CommentStatus.java` — enum ACTIVE/DELETED
- `model/MovieRating.java` — JPA entity, unique (movie_id, user_id)
- `model/MovieComment.java` — JPA entity, soft-delete via CommentStatus
- `model/CommentReaction.java` — JPA entity, unique (comment_id, user_id)
- `repository/MovieRatingRepository.java` — avg/count JPQL queries
- `repository/MovieCommentRepository.java` — paginated finder + count
- `repository/CommentReactionRepository.java` — like/dislike count queries
- `dto/CreateRatingRequest.java` — record, @Min(1) @Max(5)
- `dto/MovieRatingDto.java` — record
- `dto/MovieRatingSummaryDto.java` — record, nullable userRating
- `dto/CreateCommentRequest.java` — record, @Size(max=2000)
- `dto/UpdateCommentRequest.java` — record
- `dto/MovieCommentDto.java` — record with likeCount/dislikeCount/userReaction
- `dto/CommentReactionRequest.java` — record
- `dto/CommentReactionDto.java` — record
- `service/MovieRatingService.java` — interface
- `service/MovieCommentService.java` — interface
- `service/CommentReactionService.java` — interface
- `service/impl/MovieRatingServiceImpl.java` — upsert rating
- `service/impl/MovieCommentServiceImpl.java` — CRUD + soft-delete + ownership
- `service/impl/CommentReactionServiceImpl.java` — toggle like/dislike
- `controller/MovieRatingController.java` — /api/movies/{id}/ratings
- `controller/MovieCommentController.java` — /api/movies/{id}/comments + /api/comments/{id}
- `controller/CommentReactionController.java` — /api/comments/{id}/reactions

### Modified files
- `dto/MovieDto.java` — added averageRating, totalRatings, commentCount fields
- `service/impl/MovieServiceImpl.java` — injected rating/comment repos, enriched toDto, added toDtoBasic static helper
- `service/impl/ShowtimeServiceImpl.java` — updated MovieServiceImpl.toDto → toDtoBasic (1 line)
- `config/SecurityConfig.java` — added /api/comments/** to GET permitAll
- `config/GlobalExceptionHandler.java` — added EntityNotFoundException, AccessDeniedException, MethodArgumentNotValidException handlers
- `api-gateway/src/main/resources/application.yml` — added movie-service-comments route

## Tasks Completed
- [x] Phase 1: Entities (CommentStatus, MovieRating, MovieComment, CommentReaction)
- [x] Phase 1: Repositories (MovieRatingRepository, MovieCommentRepository, CommentReactionRepository)
- [x] Phase 2: DTOs (8 new records, MovieDto extended)
- [x] Phase 2: Service interfaces (3 interfaces)
- [x] Phase 2: Service impls (3 impls + MovieServiceImpl updated)
- [x] Phase 3: Controllers (3 new controllers)
- [x] Phase 3: Security updated (comments GET public)
- [x] Phase 3: GlobalExceptionHandler enriched
- [x] Phase 3: API gateway route added

## Tests Status
- Type check / compile: PASS (mvn -pl movie-service clean compile -q)
- Unit tests: not run (out of scope for this phase)

## Issues Encountered
- `ShowtimeServiceImpl` called `MovieServiceImpl.toDto(Movie)` as public static — broke when made private. Fixed by introducing `toDtoBasic(Movie)` public static that returns MovieDto with zeroed aggregation fields, used for embedded movie summaries inside ShowtimeDto.

## Next Steps
- Run DB migrations (DDL for movie_ratings, movie_comments, comment_reactions tables)
- Write unit/integration tests for rating, comment, reaction services
- Update docs (codebase-summary, system-architecture)
