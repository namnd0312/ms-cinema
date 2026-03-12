# Code Review: Movie Voting & Comments Feature

**Date:** 2026-03-12
**Reviewer:** code-reviewer agent
**Score: 8 / 10**

---

## Scope

- **Files reviewed:** 22 files (3 entities, 1 enum, 3 repos, 3 service impls, 3 controllers, 6 DTOs, MovieServiceImpl, SecurityConfig, GlobalExceptionHandler, application.yml, 3 test files)
- **Lines analyzed:** ~900 LOC (production) + ~400 LOC (tests)
- **Review focus:** New movie-vote-comment feature — correctness, security, pattern consistency, edge cases
- **Build:** `mvn -pl movie-service clean compile` — PASS (no errors)
- **Tests:** 18 / 18 passing (`mvn -pl movie-service test`)
- **Plan file:** `/plans/260312-1954-movie-vote-comment/`

---

## Overall Assessment

Solid implementation. Entities, repositories, services and DTOs are clean, follow existing patterns, and compile/test without issues. Security baseline is correct (userId always from JWT, ownership checks in service layer). Two bugs found: one medium (broken DELETE reaction endpoint), one low (missing `@Size` min on content). Plan action items from the validation phase were partially diverged from — the implementation went with query-time aggregation on ratings (not cached columns), which is acceptable and was re-confirmed in the architecture notes.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — `DELETE /api/comments/{commentId}/reactions` does nothing (logic bug)

**File:** `CommentReactionController.java` lines 42–44

```java
@DeleteMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<CommentReactionDto> remove(@PathVariable Long commentId) {
    return ResponseEntity.ok(reactionService.getReactionSummary(commentId, extractUserId()));
}
```

This endpoint calls `getReactionSummary` instead of removing the reaction. The service interface only exposes `toggleReaction` and `getReactionSummary` — there is no explicit `removeReaction` method. Two options:

**Option A** (preferred, KISS) — remove this endpoint entirely; the `POST /reactions` toggle-off already handles removal when the same reaction type is re-sent:
```java
// Remove DELETE endpoint from CommentReactionController — toggle-off via POST is sufficient
```

**Option B** — add `removeReaction(Long commentId, Long userId)` to service + impl that hard-deletes the current user's reaction if present, and wire it here with `ResponseEntity.noContent()`.

The current state is misleading: the endpoint returns 200 with counts but does not delete anything. Any client that calls `DELETE` to explicitly remove a reaction will be silently ignored.

---

## Medium Priority Improvements

### M1 — Plan action items not all reflected in code (plan/impl divergence)

The `plan.md` validation section lists action items that required code changes:

```
- [ ] Phase 1: Add `averageRating` and `totalRatings` columns to Movie entity  (NOT done — intentional divergence)
- [ ] Phase 2: Remove `MovieRatingRepository` injection from `MovieServiceImpl.toDto()` — use cached fields  (NOT done)
```

The implementation chose query-time aggregation (3 extra queries per `toDto` call in `MovieServiceImpl`). This is noted as accepted in the architecture decisions section. However, the plan.md still shows these as unchecked `[ ]` items, which creates confusion. **Update plan.md** to reflect the final decision.

### M2 — `MovieServiceImpl.findAll()` has N+1 for ratings and comment counts

**File:** `MovieServiceImpl.java` lines 33–37, `toDto()` lines 87–91

For each movie in `findAll()`, `toDto()` fires 3 queries (avg rating, count ratings, count comments). With 100 movies, that's 301 queries per `GET /api/movies`. The architecture decision accepted N+1 at current scale — that is reasonable — but this should be documented as a known limitation and tracked for optimization when movie count grows.

No code change needed now; add a comment:
```java
// PERF: 3 extra queries per movie (avg, totalRatings, commentCount).
// Acceptable at current scale; consider caching or batch aggregation when > 500 movies.
private MovieDto toDto(Movie m) {
```

### M3 — `CreateCommentRequest` has no minimum length constraint

**File:** `CreateCommentRequest.java` line 11

```java
@NotBlank @Size(max = 2000) String content
```

`@NotBlank` prevents empty/whitespace-only strings but `@Size(min=1)` is redundant with it. However, a user can submit a single character. Consider `@Size(min = 2, max = 2000)` to prevent noise comments, depending on product requirements.

### M4 — `CommentReactionController.DELETE` returns `ResponseEntity<CommentReactionDto>` instead of `ResponseEntity<Void>`

**File:** `CommentReactionController.java` line 42

Even if fixed to actually delete, a DELETE endpoint conventionally returns `204 No Content` + `ResponseEntity<Void>`, not a DTO body. Align with `MovieCommentController.delete()` pattern (line 68–73) which returns `ResponseEntity.noContent().build()`.

### M5 — `SecurityConfig` permits all GET on `/api/comments/**`

**File:** `SecurityConfig.java` line 33

```java
.requestMatchers(HttpMethod.GET, "/api/comments/**").permitAll()
```

`/api/comments/{commentId}/reactions` is the only GET under this path. This is fine functionally, but the pattern is slightly inconsistent — reaction counts on a GET are public (no auth needed), which is reasonable for the use case. Worth noting that any future GET endpoint added under `/api/comments/` will be automatically public.

---

## Low Priority Suggestions

### L1 — `extractUserId()` duplicated across three controllers

`MovieRatingController`, `MovieCommentController`, and `CommentReactionController` each have identical `extractUserId()` and `extractUserIdOrNull()` private methods. Consider extracting to a shared `SecurityUtils` or `ControllerUtils` class:

```java
// util/SecurityUtils.java
public final class SecurityUtils {
    public static Long extractUserId() { ... }
    public static Long extractUserIdOrNull() { ... }
}
```

Not critical — 3 files × 8 lines each is acceptable duplication — but worth consolidating if a 4th controller is added.

### L2 — `findActiveOrThrow` duplicated in `MovieCommentServiceImpl` and `CommentReactionServiceImpl`

Both services load a `MovieComment` and throw if `DELETED`. The logic is identical. Only relevant if a 3rd service ever needs it; skip for now (YAGNI).

### L3 — `DELETE /api/comments/{id}` returns `ResponseEntity<Void>` but `CommentStatus.DELETED` is never exposed in response

Soft-delete is invisible to caller on success — correct. No action needed.

### L4 — `init-databases.sql` adds `testdb` to the init script

**File:** `init-databases.sql` (modified)

```sql
CREATE DATABASE testdb;
```

This creates a `testdb` database on every fresh PostgreSQL container. If tests use H2 in-memory, this is unnecessary noise in production init script. If it is used by a Testcontainer or docker-compose test profile, it belongs in a test-only init. Recommend removing or moving to a `docker/test-init.sql`.

### L5 — `getRatingSummary` rounding: `Math.round(avg * 10.0) / 10.0`

**Files:** `MovieRatingServiceImpl.java:62`, `MovieServiceImpl.java:114`

Same rounding logic duplicated. Minor, but a shared `RatingUtils.round(Double avg)` would prevent divergence if precision changes.

### L6 — Missing test coverage gaps vs plan

Plan phase 4 defined 35 test cases across 8 test files. Implemented: 18 tests in 3 service unit test files only. Missing:
- Repository tests (`MovieRatingRepositoryTest`, `CommentReactionRepositoryTest`)
- Controller integration tests (all 3)

This was likely a scoping decision. For a future tester pass, the missing tests are:
- `MovieRatingRepositoryTest` — 3 cases
- `CommentReactionRepositoryTest` — 3 cases
- `MovieRatingControllerTest` — 4 cases
- `MovieCommentControllerTest` — 5 cases
- `CommentReactionControllerTest` — 3 cases

---

## Positive Observations

- **Security is correct:** userId always extracted from JWT principal, never from request body/params. Ownership enforced in service layer, not just controller layer. Admin flag derived from `user.roles()` from JWT.
- **Toggle logic is clean and correct:** All 3 cases (create, switch, remove-same) properly handled with clear conditional flow and good comments.
- **Soft-delete handled consistently:** `findActiveOrThrow()` helper correctly hides DELETED comments from all reads and mutations.
- **Null-safe public GET:** `extractUserIdOrNull()` pattern correctly handles unauthenticated callers on public GET endpoints.
- **Entities follow existing patterns exactly:** Same Lombok annotations, `@PrePersist`/`@PreUpdate`, `FetchType.LAZY`, `userId` as plain Long (cross-service pattern).
- **Validation on DTOs is appropriate:** `@NotNull @Min(1) @Max(5)` on rating, `@NotBlank @Size(max=2000)` on content, `@NotNull` on `isLike`.
- **GlobalExceptionHandler covers all needed cases:** `EntityNotFoundException→404`, `AccessDeniedException→403`, `MethodArgumentNotValidException→400`, generic `Exception→500`.
- **Gateway route added correctly:** `/api/comments/**` route present in `application.yml`.
- **All 18 unit tests pass** with good AAA structure and descriptive names.
- **Compile clean:** zero warnings or errors.

---

## Recommended Actions

1. **[HIGH] Fix `DELETE /api/comments/{commentId}/reactions`** — either remove the endpoint (POST toggle-off is sufficient) or implement a real `removeReaction` service method returning `204 No Content`.
2. **[MEDIUM] Update `plan.md`** — mark completed phases and revise the unchecked action items to reflect the query-time aggregation decision.
3. **[LOW] Extract `extractUserId()`/`extractUserIdOrNull()` to `SecurityUtils`** — prevents drift if more controllers are added.
4. **[LOW] Remove `CREATE DATABASE testdb` from `init-databases.sql`** — or move to test-only init file.
5. **[LOW] Add a `PERF` comment in `MovieServiceImpl.toDto()`** — document the N+1 acceptance decision.
6. **[FUTURE] Add missing controller + repository integration tests** (phase 4 remaining items).

---

## Metrics

- **Compile:** PASS (0 errors, 0 warnings)
- **Tests:** 18 / 18 PASS
- **Test files created vs planned:** 3 / 8 (service unit tests only)
- **Linting issues:** 0 blocking; 2 style (duplication)
- **Security issues:** 0 critical; 0 high
- **Bugs found:** 1 medium (broken DELETE reaction), 1 low (testdb in init sql)

---

## Unresolved Questions

1. Should `DELETE /api/comments/{commentId}/reactions` be kept as an explicit "force remove" endpoint, or is POST toggle-off sufficient for all clients? (Product decision needed before fix.)
2. Is `testdb` in `init-databases.sql` intentional for a docker-compose integration test profile, or leftover from local dev?
