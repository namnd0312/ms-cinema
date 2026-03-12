import { Component, input, output, signal, computed, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MovieCommentDto } from '../../../core/models/movie-comment.model';
import { MovieCommentService } from '../../../core/services/movie-comment.service';

@Component({
  selector: 'app-comment-item',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, FormsModule],
  template: `
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
          <textarea class="edit-textarea" [(ngModel)]="editContent" maxlength="2000" rows="3"></textarea>
          <div class="edit-hint">{{ editContent.length }}/2000</div>
          <div class="edit-actions">
            <button mat-button (click)="cancelEdit()">Cancel</button>
            <button mat-raised-button color="primary" (click)="saveEdit()" [disabled]="!editContent.trim()">Save</button>
          </div>
        </div>
      } @else {
        <p class="comment-content">{{ comment().content }}</p>
      }
      <div class="reaction-buttons">
        <button mat-button [class.active]="localUserReaction() === 'LIKE'" [disabled]="reacting() || !currentUserId()" (click)="onReact(true)">
          <mat-icon>{{ localUserReaction() === 'LIKE' ? 'thumb_up' : 'thumb_up_off_alt' }}</mat-icon>
          <span>{{ localLikeCount() }}</span>
        </button>
        <button mat-button [class.active]="localUserReaction() === 'DISLIKE'" [disabled]="reacting() || !currentUserId()" (click)="onReact(false)">
          <mat-icon>{{ localUserReaction() === 'DISLIKE' ? 'thumb_down' : 'thumb_down_off_alt' }}</mat-icon>
          <span>{{ localDislikeCount() }}</span>
        </button>
      </div>
    </div>
  `,
  styles: [`
    .comment-item { padding: 16px 0; border-bottom: 1px solid rgba(255,255,255,0.1); }
    .comment-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
    .comment-author { font-weight: 500; color: #ffc107; }
    .comment-time { font-size: 0.8rem; color: rgba(255,255,255,0.4); }
    .comment-actions { margin-left: auto; display: flex; }
    .comment-actions button { opacity: 0.6; }
    .comment-actions button:hover { opacity: 1; }
    .comment-content { margin: 0; line-height: 1.5; color: rgba(255,255,255,0.85); white-space: pre-wrap; }
    .edit-form { margin-top: 8px; }
    .edit-textarea { width: 100%; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; color: white; padding: 8px; font-family: inherit; resize: vertical; box-sizing: border-box; }
    .edit-hint { text-align: right; font-size: 0.75rem; color: rgba(255,255,255,0.4); margin: 4px 0; }
    .edit-actions { display: flex; gap: 8px; justify-content: flex-end; }
    .reaction-buttons { display: flex; gap: 8px; margin-top: 8px; }
    .reaction-buttons button { min-width: auto; padding: 4px 12px; font-size: 0.85rem; }
    .reaction-buttons button.active { color: #ffc107; }
    .reaction-buttons mat-icon { font-size: 18px; height: 18px; width: 18px; margin-right: 4px; }
    .reaction-buttons button:disabled { opacity: 0.5; }
  `]
})
export class CommentItemComponent {
  comment = input.required<MovieCommentDto>();
  currentUserId = input<number | null>(null);
  isAdmin = input<boolean>(false);
  editComment = output<{ commentId: number; content: string }>();
  deleteComment = output<number>();

  localLikeCount = signal(0);
  localDislikeCount = signal(0);
  localUserReaction = signal<'LIKE' | 'DISLIKE' | null>(null);
  reacting = signal(false);
  editing = signal(false);
  editContent = '';

  isOwner = computed(() => this.currentUserId() === this.comment().userId);

  private commentService = inject(MovieCommentService);

  constructor() {
    effect(() => {
      const c = this.comment();
      this.localLikeCount.set(c.likeCount);
      this.localDislikeCount.set(c.dislikeCount);
      this.localUserReaction.set(c.userReaction);
    });
  }

  timeAgo(dateStr: string): string {
    const seconds = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
    if (seconds < 60) return 'just now';
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    if (seconds < 2592000) return `${Math.floor(seconds / 86400)}d ago`;
    return new Date(dateStr).toLocaleDateString();
  }

  startEdit(): void {
    this.editContent = this.comment().content;
    this.editing.set(true);
  }

  cancelEdit(): void { this.editing.set(false); }

  saveEdit(): void {
    if (this.editContent.trim()) {
      this.editComment.emit({ commentId: this.comment().id, content: this.editContent.trim() });
      this.editing.set(false);
    }
  }

  confirmDelete(): void {
    if (confirm('Delete this comment?')) {
      this.deleteComment.emit(this.comment().id);
    }
  }

  onReact(isLike: boolean): void {
    if (this.reacting()) return;
    this.reacting.set(true);
    const current = this.localUserReaction();
    const clickedSame = (isLike && current === 'LIKE') || (!isLike && current === 'DISLIKE');
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
}
