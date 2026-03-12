import { Component, input, signal, computed, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MovieCommentDto } from '../../../core/models/movie-comment.model';
import { MovieCommentService } from '../../../core/services/movie-comment.service';
import { AuthService } from '../../../core/services/auth.service';
import { CommentItemComponent } from '../comment-item/comment-item.component';

@Component({
  selector: 'app-comment-list',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSnackBarModule,
    CommentItemComponent
  ],
  template: `
    <div class="comments-section">
      <h2>Comments</h2>

      @if (isAuthenticated()) {
        <div class="add-comment">
          <textarea class="comment-textarea" [(ngModel)]="newCommentContent" maxlength="2000" rows="3"
                    placeholder="Share your thoughts..."></textarea>
          <div class="comment-form-footer">
            <span class="char-count">{{ newCommentContent.length }}/2000</span>
            <button mat-raised-button color="primary" (click)="submitComment()"
                    [disabled]="!newCommentContent.trim() || submitting()">
              Post Comment
            </button>
          </div>
        </div>
      }

      @if (loading()) {
        <div class="loading"><mat-spinner diameter="32"></mat-spinner></div>
      }

      @if (!loading() && comments().length === 0) {
        <p class="empty-state">No comments yet. Be the first to comment!</p>
      }

      @for (comment of comments(); track comment.id) {
        <app-comment-item
          [comment]="comment"
          [currentUserId]="currentUserId()"
          [isAdmin]="isAdmin()"
          (editComment)="onEditComment($event)"
          (deleteComment)="onDeleteComment($event)"
        />
      }

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
  `,
  styles: [`
    .comments-section { margin-top: 24px; }
    .comments-section h2 { margin-bottom: 16px; }
    .add-comment { margin-bottom: 24px; }
    .comment-textarea { width: 100%; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.2); border-radius: 8px; color: white; padding: 12px; font-family: inherit; font-size: 0.95rem; resize: vertical; box-sizing: border-box; }
    .comment-textarea:focus { outline: none; border-color: #ffc107; }
    .comment-form-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
    .char-count { font-size: 0.8rem; color: rgba(255,255,255,0.4); }
    .loading { display: flex; justify-content: center; padding: 24px; }
    .empty-state { text-align: center; color: rgba(255,255,255,0.5); padding: 32px; }
  `]
})
export class CommentListComponent implements OnInit {
  movieId = input.required<number>();

  private commentService = inject(MovieCommentService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  comments = signal<MovieCommentDto[]>([]);
  totalElements = signal(0);
  pageIndex = signal(0);
  loading = signal(false);
  submitting = signal(false);
  newCommentContent = '';
  pageSize = 20;

  isAuthenticated = computed(() => this.authService.isAuthenticated());
  currentUserId = computed(() => this.authService.currentUser()?.id ?? null);
  isAdmin = computed(() => this.authService.hasRole('ROLE_ADMIN'));

  ngOnInit(): void { this.loadComments(); }

  loadComments(): void {
    this.loading.set(true);
    this.commentService.getComments(this.movieId(), this.pageIndex(), this.pageSize).subscribe({
      next: (page) => {
        this.comments.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  submitComment(): void {
    if (!this.newCommentContent.trim()) return;
    this.submitting.set(true);
    this.commentService.createComment(this.movieId(), { content: this.newCommentContent.trim() }).subscribe({
      next: () => {
        this.newCommentContent = '';
        this.pageIndex.set(0);
        this.loadComments();
        this.submitting.set(false);
        this.snackBar.open('Comment posted!', 'Close', { duration: 2000 });
      },
      error: () => this.submitting.set(false)
    });
  }

  onEditComment(event: { commentId: number; content: string }): void {
    this.commentService.updateComment(event.commentId, { content: event.content }).subscribe({
      next: () => {
        this.loadComments();
        this.snackBar.open('Comment updated!', 'Close', { duration: 2000 });
      }
    });
  }

  onDeleteComment(commentId: number): void {
    this.commentService.deleteComment(commentId).subscribe({
      next: () => {
        this.loadComments();
        this.snackBar.open('Comment deleted.', 'Close', { duration: 2000 });
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.loadComments();
  }
}
