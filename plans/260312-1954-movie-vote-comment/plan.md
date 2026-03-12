---
title: "Movie Voting & Comments"
description: "Add star ratings, flat comments, and comment like/dislike to movie-service"
status: in-review
priority: P2
effort: 8h
branch: master
tags: [movie-service, ratings, comments, likes]
created: 2026-03-12
---

# Movie Voting & Comments - Implementation Plan

## Overview

Add three features to movie-service: star ratings (1-5), flat comments, and comment like/dislike reactions. All write ops require JWT auth. Follows existing Interface+Impl, Lombok, record DTO patterns.

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Database Entities & Repositories | complete | 2h | [phase-01](phase-01-entities-and-repositories.md) |
| 2 | DTOs & Service Layer | complete | 2.5h | [phase-02](phase-02-dtos-and-services.md) |
| 3 | REST Controllers & Security | complete | 2h | [phase-03](phase-03-controllers-and-security.md) |
| 4 | Testing & Integration | partial | 1.5h | [phase-04](phase-04-testing-and-integration.md) |

## Key Dependencies

- `com.namnd:jwt-auth-spring-boot-starter` - JwtAuthenticatedUser principal for userId extraction
- Hibernate `ddl-auto: update` - auto-creates tables from new entities
- Existing `Movie` entity - FK target for ratings/comments
- `SecurityConfig` - needs GET permit updates for new endpoints

## Architecture Decisions

1. **Query-time avg rating** - ~~cached columns on Movie~~ REVISED: query-time AVG/COUNT via `MovieRatingRepository` injected into `MovieServiceImpl.toDto()`. Accepted N+1 at current scale. Cached-columns approach was validated but not implemented — query-time chosen for simplicity.
2. **Hard delete for likes** - toggle semantics; no audit trail needed
3. **Soft delete for comments** - status enum, aligns with Movie pattern
4. **No Kafka events** - not needed until another service consumes rating/comment data
5. **Offset pagination** - Spring `Pageable` for comments; cursor not needed at current scale
6. **isLike boolean** - single CommentReaction entity with `isLike` field (true=like, false=dislike)
7. **Toggle off on same reaction** - clicking same reaction type removes it (YouTube/Reddit behavior)
8. **Admin + owner comment delete** - admins can delete any comment; owners can delete own
9. **Separate /api/comments path** - needs gateway route addition for /api/comments/** → movie-service

## New Files Summary

- 3 entities, 3 repositories, 3 service interfaces, 3 service impls
- 6 DTOs (3 request, 3 response records)
- 3 controllers
- SecurityConfig update, GlobalExceptionHandler update
- MovieServiceImpl enriches MovieDto with query-time aggregations (no cached columns)
- API Gateway route addition for /api/comments/**

## Risk

- **Race conditions on cached rating update**: use `@Transactional` + unique constraint (DB enforces)
- **Race conditions on upsert**: use `@Transactional` + unique constraint (DB enforces)

## Validation Summary

**Validated:** 2026-03-12
**Questions asked:** 4

### Confirmed Decisions
- **Rating aggregation**: Cached columns on Movie (not query-time AVG) — faster reads for movie lists
- **Reaction toggle**: Same reaction click removes it (toggle off) — standard UX
- **Comment moderation**: Owner + Admin can delete comments — basic content moderation
- **API path**: Separate `/api/comments/**` path — cleaner REST, requires gateway route

### Action Items — Final Status

- [x] ~~Phase 1: Add `averageRating`/`totalRatings` cached columns to Movie~~ — REVISED: query-time aggregation chosen; Movie entity unchanged
- [x] ~~Phase 2: Update `MovieRatingServiceImpl` to write cached fields~~ — REVISED: not needed; query-time approach used
- [x] ~~Phase 2: Remove `MovieRatingRepository` from `MovieServiceImpl.toDto()`~~ — REVISED: kept intentionally for query-time aggregation
- [x] Phase 2: `MovieCommentServiceImpl.deleteComment()` allows admin OR owner — DONE (`isAdmin` flag from controller)
- [x] Phase 3: `CommentController.delete()` passes `isAdmin` from JWT roles — DONE
- [x] Phase 4: Gateway route `/api/comments/**` → movie-service — DONE
- [x] Phase 4: Admin comment deletion test case — DONE (`deleteComment_adminDeletes_setsStatusDeleted`)

### Remaining Issues (from code review 2026-03-12)

- [ ] **BUG [HIGH]:** `DELETE /api/comments/{commentId}/reactions` calls `getReactionSummary` instead of removing — fix or remove endpoint
- [ ] **[LOW]:** Remove `CREATE DATABASE testdb` from `init-databases.sql` or move to test-only init file
- [ ] **[LOW]:** Extract `extractUserId()`/`extractUserIdOrNull()` to `SecurityUtils` shared helper
- [ ] **[FUTURE]:** Add repository + controller integration tests (5 files, 18 test cases per phase-04 plan)
