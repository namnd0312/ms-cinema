---
title: "Frontend Movie Voting & Comments"
description: "Add star rating, comments with pagination, and like/dislike reactions to movie detail page"
status: pending
priority: P1
effort: 6h
branch: master
tags: [frontend, angular, movie-rating, comments, reactions]
created: 2026-03-12
---

# Frontend Movie Voting & Comments

## Summary

Add movie rating (1-5 stars), comment section with pagination, and comment reaction (like/dislike) features to the Angular frontend. Backend API is already implemented and live.

## Codebase Patterns (Key Findings)

- **Angular 18** with standalone components, signals, `inject()` pattern
- **Angular Material** used throughout (MatCard, MatIcon, MatButton, MatChips, MatSnackBar, etc.)
- Services use `HttpClient` with `/api/` prefix (proxied to gateway:8080)
- Auth: `AuthService` with signals (`currentUser`, `isAuthenticated`), JWT in localStorage
- Auth interceptor auto-attaches tokens; PUBLIC_URLS skip auth (needs update for GET ratings/comments)
- Error interceptor shows MatSnackBar for errors
- Movie detail page: `MovieDetailComponent` — standalone, inline template/styles, signals
- Components use `input()`, `output()`, `signal()` APIs

## Phase Overview

| Phase | Description | Effort | Status |
|-------|-------------|--------|--------|
| [Phase 1](./phase-01-services-and-models.md) | TypeScript models + Angular services for ratings/comments/reactions | 1h | pending |
| [Phase 2](./phase-02-star-rating-component.md) | Star rating component on movie detail page | 1.5h | pending |
| [Phase 3](./phase-03-comments-section-with-pagination.md) | Comments list, create, edit, delete with pagination | 2.5h | pending |
| [Phase 4](./phase-04-comment-reaction-like-dislike-buttons.md) | Like/dislike buttons on comments | 1h | pending |

## Key Dependencies

- Backend API endpoints all live on gateway port 8080
- Auth interceptor PUBLIC_URLS must include GET endpoints for ratings/comments
- `MovieDetailComponent` is the integration point for all new components

## Architecture

```
movie-detail/
  movie-detail.component.ts       (modified — integrates new components)

core/models/
  movie-rating.model.ts           (new)
  movie-comment.model.ts          (new)

core/services/
  movie-rating.service.ts         (new)
  movie-comment.service.ts        (new)

features/movies/
  star-rating/
    star-rating.component.ts      (new — reusable star display + input)
  comment-list/
    comment-list.component.ts     (new — paginated list + create form)
  comment-item/
    comment-item.component.ts     (new — single comment with edit/delete/reactions)
```
