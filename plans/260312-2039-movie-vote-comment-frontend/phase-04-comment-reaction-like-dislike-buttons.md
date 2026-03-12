# Phase 4: Comment Reaction Like/Dislike Buttons

## Context Links
- [Plan Overview](./plan.md)
- [Phase 3 — Comments Section](./phase-03-comments-section-with-pagination.md)
- Comment item component: `cinema-frontend/src/app/features/movies/comment-item/comment-item.component.ts`
- Backend endpoints: `POST /api/comments/{commentId}/reactions`, `DELETE /api/comments/{commentId}/reactions`

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Add like/dislike toggle buttons to each comment with counts. Authenticated users can react; clicking same reaction removes it, clicking opposite switches it.

## Key Insights
- `MovieCommentDto` already includes `likeCount`, `dislikeCount`, `userReaction` fields
- `userReaction` is `'LIKE' | 'DISLIKE' | null` — null when not logged in or no reaction
- POST `/api/comments/{id}/reactions` with `{ isLike: true/false }` creates or updates reaction
- DELETE `/api/comments/{id}/reactions` removes reaction entirely
- Toggle logic: if user clicks same reaction → DELETE (remove); if different → POST (switch)
- Response `CommentReactionDto` returns updated counts — use for optimistic-like update

## Requirements
- **Functional:**
  - Display like count + thumb_up icon, dislike count + thumb_down icon per comment
  - Authenticated users: clickable buttons with toggle behavior
  - Active reaction highlighted (filled icon vs outlined)
  - Anonymous users: buttons visible but disabled/non-interactive
  - Counts update immediately after reaction
- **Non-functional:**
  - Debounce rapid clicks (disable button during API call)
  - Minimal layout shift when counts change

## Related Code Files

### Files to Modify
- `cinema-frontend/src/app/features/movies/comment-item/comment-item.component.ts` — add reaction buttons and logic

### No New Files
Reactions are part of the comment-item component. No separate component needed (YAGNI).

## Architecture

Reaction logic lives in `CommentItemComponent`. The component already has access to `comment()` which contains `likeCount`, `dislikeCount`, `userReaction`.

**Data flow:**
- Reaction buttons rendered inside `comment-item` below comment content
- Click → determine action (POST or DELETE) → call service → update local comment signals
- Parent `comment-list` does NOT need to refetch — reaction response includes updated counts

## Implementation Steps

### Step 1: Add reaction state to `CommentItemComponent`

Add signals for local reaction state (so we can update without refetching):
```typescript
localLikeCount = signal(0);
localDislikeCount = signal(0);
localUserReaction = signal<'LIKE' | 'DISLIKE' | null>(null);
reacting = signal(false); // prevents rapid clicks

// Initialize from input comment
ngOnInit() or use effect():
  this.localLikeCount.set(this.comment().likeCount);
  this.localDislikeCount.set(this.comment().dislikeCount);
  this.localUserReaction.set(this.comment().userReaction);
```

### Step 2: Add reaction template

Insert below comment content (and above the Phase 4 placeholder comment):
```html
<div class="reaction-buttons">
  <button mat-button
    [class.active]="localUserReaction() === 'LIKE'"
    [disabled]="reacting() || !currentUserId()"
    (click)="onReact(true)">
    <mat-icon>{{ localUserReaction() === 'LIKE' ? 'thumb_up' : 'thumb_up_off_alt' }}</mat-icon>
    <span>{{ localLikeCount() }}</span>
  </button>
  <button mat-button
    [class.active]="localUserReaction() === 'DISLIKE'"
    [disabled]="reacting() || !currentUserId()"
    (click)="onReact(false)">
    <mat-icon>{{ localUserReaction() === 'DISLIKE' ? 'thumb_down' : 'thumb_down_off_alt' }}</mat-icon>
    <span>{{ localDislikeCount() }}</span>
  </button>
</div>
```

### Step 3: Add reaction styles

```css
.reaction-buttons { display: flex; gap: 8px; margin-top: 8px; }
.reaction-buttons button { min-width: auto; padding: 4px 12px; font-size: 0.85rem; }
.reaction-buttons button.active { color: #ffc107; }
.reaction-buttons mat-icon { font-size: 18px; height: 18px; width: 18px; margin-right: 4px; }
.reaction-buttons button:disabled { opacity: 0.5; cursor: default; }
```

### Step 4: Implement `onReact(isLike: boolean)` method

```typescript
private commentService = inject(MovieCommentService);

onReact(isLike: boolean): void {
  if (this.reacting()) return;
  this.reacting.set(true);

  const currentReaction = this.localUserReaction();
  const clickedSame = (isLike && currentReaction === 'LIKE') || (!isLike && currentReaction === 'DISLIKE');

  const request$ = clickedSame
    ? this.commentService.removeReaction(this.comment().id)
    : this.commentService.reactToComment(this.comment().id, { isLike });

  request$.subscribe({
    next: (dto) => {
      this.localLikeCount.set(dto.likeCount);
      this.localDislikeCount.set(dto.dislikeCount);
      this.localUserReaction.set(dto.userReaction);
      this.reacting.set(false);
    },
    error: () => this.reacting.set(false)
  });
}
```

### Step 5: Inject `MovieCommentService` into `CommentItemComponent`

Add `private commentService = inject(MovieCommentService)` to the component.

## Todo List
- [ ] Add local reaction signals to `comment-item.component.ts`
- [ ] Initialize local state from comment input (use `effect()` or `ngOnInit`)
- [ ] Add reaction buttons template with toggle icons
- [ ] Add reaction button styles
- [ ] Implement `onReact()` with toggle logic (same = remove, different = switch)
- [ ] Disable buttons during API call (reacting signal)
- [ ] Disable buttons for anonymous users (no currentUserId)
- [ ] Compile check
- [ ] Test: like/dislike toggle cycle (like → unlike → dislike → remove)
- [ ] Test: anonymous user sees counts but buttons disabled
- [ ] Test: counts update immediately after reaction

## Success Criteria
- Like/dislike buttons display with counts on every comment
- Clicking like when no reaction → POST like → filled thumb_up, count +1
- Clicking like again → DELETE → outlined thumb_up, count -1
- Clicking dislike when liked → POST dislike → switches icons and counts
- Buttons disabled during API call (no double-submit)
- Anonymous users see read-only counts

## Risk Assessment
- **Race conditions on rapid clicks:** `reacting` signal disables buttons during API call. Sufficient for normal use.
- **Stale counts after page navigation:** When user pages through comments, fresh data loaded from API. No staleness issue.

## Security Considerations
- Reactions require JWT — backend rejects unauthenticated requests
- Frontend disables buttons for anonymous users (UX guard only)
- One reaction per user per comment enforced by backend
