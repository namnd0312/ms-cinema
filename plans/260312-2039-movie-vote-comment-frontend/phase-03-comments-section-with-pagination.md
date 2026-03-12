# Phase 3: Comments Section with Pagination

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1 — Services & Models](./phase-01-services-and-models.md)
- [Phase 2 — Star Rating](./phase-02-star-rating-component.md)
- Movie detail page: `cinema-frontend/src/app/features/movies/movie-detail/movie-detail.component.ts`
- Error handling pattern: `cinema-frontend/src/app/core/interceptors/error.interceptor.ts` (MatSnackBar)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Build a comments section below showtimes on the movie detail page. Includes paginated comment list, create comment form, edit/delete own comments.

## Key Insights
- Backend returns Spring `Page<MovieCommentDto>` with `page` and `size` params (default 20)
- Edit/delete restricted to comment owner (backend checks via JWT userId)
- Admin can delete any comment (check `AuthService.hasRole('ROLE_ADMIN')`)
- `MovieCommentDto` includes `userId` — compare with `AuthService.currentUser().id` for ownership
- Use `MatPaginator` for pagination (already available in Angular Material)
- Keep comment-list and comment-item as separate components for modularity (<200 lines each)

## Requirements
- **Functional:**
  - Display paginated comment list (20 per page)
  - Authenticated users: show "Add a comment" textarea + submit button
  - Comment owner: show edit/delete actions on their comments
  - Admin: show delete action on all comments
  - Edit: inline edit mode (replace text with textarea, save/cancel buttons)
  - Delete: confirm dialog before deleting
  - Show commenter userId (backend doesn't return username — display "User #ID" for now)
  - Show relative timestamp (e.g., "2 hours ago")
- **Non-functional:**
  - Paginator at bottom of comment list
  - Empty state: "No comments yet. Be the first to comment!"
  - Loading spinner while fetching comments
  - Max 2000 chars for comment content (with character counter)

## Related Code Files

### Files to Create
- `cinema-frontend/src/app/features/movies/comment-list/comment-list.component.ts`
- `cinema-frontend/src/app/features/movies/comment-item/comment-item.component.ts`

### Files to Modify
- `cinema-frontend/src/app/features/movies/movie-detail/movie-detail.component.ts` — add `<app-comment-list>` below showtimes section

## Architecture

```
MovieDetailComponent
  └── CommentListComponent (inputs: movieId)
        ├── Comment form (textarea + submit) — only if authenticated
        ├── CommentItemComponent (for each comment)
        │     ├── Display: content, userId, timestamp, like/dislike counts
        │     ├── Edit mode: textarea + save/cancel
        │     └── Delete: confirmation → emit delete event
        └── MatPaginator
```

**Data flow:**
- `CommentListComponent` owns the data — fetches comments, handles create/pagination
- `CommentItemComponent` is presentational — emits `edit`, `delete` events upward
- Parent handles API calls, refreshes list on success

## Implementation Steps

### Step 1: Create `comment-item.component.ts`

**Inputs:**
- `comment: MovieCommentDto` (required)
- `currentUserId: number | null`
- `isAdmin: boolean`

**Outputs:**
- `editComment: EventEmitter<{ commentId: number, content: string }>`
- `deleteComment: EventEmitter<number>` (commentId)

**Template:**
```html
<div class="comment-item">
  <div class="comment-header">
    <span class="comment-author">User #{{ comment().userId }}</span>
    <span class="comment-time">{{ timeAgo(comment().createdAt) }}</span>
    @if (isOwner() || isAdmin()) {
      <div class="comment-actions">
        @if (isOwner()) {
          <button mat-icon-button (click)="startEdit()"><mat-icon>edit</mat-icon></button>
        }
        <button mat-icon-button (click)="confirmDelete()"><mat-icon>delete</mat-icon></button>
      </div>
    }
  </div>

  @if (editing()) {
    <div class="edit-form">
      <mat-form-field appearance="outline" class="full-width">
        <textarea matInput [(ngModel)]="editContent" maxlength="2000" rows="3"></textarea>
        <mat-hint align="end">{{ editContent.length }}/2000</mat-hint>
      </mat-form-field>
      <div class="edit-actions">
        <button mat-button (click)="cancelEdit()">Cancel</button>
        <button mat-raised-button color="primary" (click)="saveEdit()" [disabled]="!editContent.trim()">Save</button>
      </div>
    </div>
  } @else {
    <p class="comment-content">{{ comment().content }}</p>
  }

  <!-- Reaction buttons go here in Phase 4 -->
</div>
```

**Component class:**
- `isOwner = computed(() => this.currentUserId() === this.comment().userId)`
- `editing = signal(false)`, `editContent = ''`
- `timeAgo(dateStr)`: simple relative time helper (minutes/hours/days ago)
- `confirmDelete()`: use `window.confirm()` for simplicity (KISS — no need for MatDialog)

**Material imports:** `MatIconModule`, `MatButtonModule`, `MatFormFieldModule`, `MatInputModule`, `FormsModule`

### Step 2: Create `comment-list.component.ts`

**Inputs:**
- `movieId: number` (required)

**Signals:**
- `comments: signal<MovieCommentDto[]>([])`
- `totalElements: signal<number>(0)`
- `pageIndex: signal<number>(0)`
- `loading: signal<boolean>(false)`
- `newCommentContent: string`

**Injections:** `MovieCommentService`, `AuthService`, `MatSnackBar`

**Template:**
```html
<div class="comments-section">
  <h2>Comments</h2>

  <!-- Add comment form (authenticated only) -->
  @if (authService.isAuthenticated()) {
    <div class="add-comment">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Add a comment</mat-label>
        <textarea matInput [(ngModel)]="newCommentContent" maxlength="2000" rows="3"
                  placeholder="Share your thoughts..."></textarea>
        <mat-hint align="end">{{ newCommentContent.length }}/2000</mat-hint>
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="submitComment()"
              [disabled]="!newCommentContent.trim() || submitting()">
        Post Comment
      </button>
    </div>
  }

  <!-- Loading -->
  @if (loading()) {
    <div class="loading"><mat-spinner diameter="32"></mat-spinner></div>
  }

  <!-- Comment list -->
  @if (!loading() && comments().length === 0) {
    <p class="empty-state">No comments yet. Be the first to comment!</p>
  }

  @for (comment of comments(); track comment.id) {
    <app-comment-item
      [comment]="comment"
      [currentUserId]="authService.currentUser()?.id ?? null"
      [isAdmin]="authService.hasRole('ROLE_ADMIN')"
      (editComment)="onEditComment($event)"
      (deleteComment)="onDeleteComment($event)"
    />
  }

  <!-- Paginator -->
  @if (totalElements() > pageSize) {
    <mat-paginator
      [length]="totalElements()"
      [pageSize]="pageSize"
      [pageIndex]="pageIndex()"
      (page)="onPageChange($event)"
      [hidePageSize]="true"
    />
  }
</div>
```

**Methods:**
- `loadComments()`: calls `movieCommentService.getComments(movieId, pageIndex, pageSize)`
- `submitComment()`: calls `createComment`, resets form, reloads page 0
- `onEditComment({commentId, content})`: calls `updateComment`, updates comment in list
- `onDeleteComment(commentId)`: calls `deleteComment`, reloads current page
- `onPageChange(event)`: update pageIndex, reload

**Material imports:** `MatFormFieldModule`, `MatInputModule`, `MatButtonModule`, `MatPaginatorModule`, `MatProgressSpinnerModule`, `FormsModule`

### Step 3: Integrate into `MovieDetailComponent`

Add below the showtimes section:
```html
<mat-divider></mat-divider>
<app-comment-list [movieId]="movie()!.id" />
```

Add to imports array: `CommentListComponent`

### Step 4: `timeAgo` utility

Create a simple helper function (inside `comment-item.component.ts` or as a separate pipe — KISS, keep inline):
```typescript
timeAgo(dateStr: string): string {
  const seconds = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 2592000) return `${Math.floor(seconds / 86400)}d ago`;
  return new Date(dateStr).toLocaleDateString();
}
```

## Todo List
- [ ] Create `comment-item.component.ts` — display, edit mode, delete confirm
- [ ] Implement `timeAgo` helper
- [ ] Create `comment-list.component.ts` — fetch, paginate, create form
- [ ] Integrate `<app-comment-list>` into `movie-detail.component.ts`
- [ ] Handle edit comment flow (inline textarea → save → API call → update list)
- [ ] Handle delete comment flow (confirm → API call → reload)
- [ ] Handle create comment flow (textarea → submit → reset → reload page 0)
- [ ] Empty state and loading state
- [ ] Character counter (2000 max)
- [ ] Paginator at bottom
- [ ] Compile check
- [ ] Test: anonymous user sees comments but no form
- [ ] Test: logged-in user can post, edit own, delete own
- [ ] Test: admin can delete any comment

## Success Criteria
- Comments load with pagination on movie detail page
- Authenticated users can create comments
- Comment owners can edit (inline) and delete (with confirmation)
- Admins can delete any comment
- Empty state displays when no comments
- Character counter shows remaining chars
- Page navigation works correctly

## Risk Assessment
- **No username in DTO:** Backend `MovieCommentDto` only includes `userId`, not username. Displaying "User #123" is not ideal. **Mitigation:** Accept for now; backend could add username later. Keep display logic in one place (`comment-item`) for easy update.
- **Optimistic updates vs refetch:** Refetch after create/edit/delete is simpler and guarantees consistency. Small perf cost acceptable for KISS.

## Security Considerations
- Never show edit button unless `currentUserId === comment.userId`
- Never show delete button unless owner or admin
- Backend enforces ownership — frontend is UX only, not security boundary
- Content maxlength enforced both in textarea and backend validation
