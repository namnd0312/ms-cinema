import { Component, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-star-rating',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="star-rating" (mouseleave)="onStarLeave()">
      <div class="stars">
        @for (i of [1, 2, 3, 4, 5]; track i) {
          <mat-icon
            class="star"
            [class.interactive]="!readonly()"
            [class.filled]="isStarFilled(i)"
            (mouseenter)="onStarHover(i)"
            (click)="onStarClick(i)">
            {{ isStarFilled(i) ? 'star' : 'star_border' }}
          </mat-icon>
        }
      </div>
      <span class="rating-text">
        {{ displayAverage() }} <span class="total">({{ totalRatings() }})</span>
      </span>
    </div>
    @if (userRating() !== null) {
      <span class="user-rating">Your rating: {{ userRating() }}/5</span>
    }
  `,
  styles: [`
    .star-rating { display: inline-flex; align-items: center; gap: 8px; }
    .stars { display: flex; gap: 2px; }
    .star { color: rgba(255,255,255,0.3); font-size: 28px; height: 28px; width: 28px; transition: color 0.15s, transform 0.15s; }
    .star.interactive { cursor: pointer; }
    .star.interactive:hover { transform: scale(1.15); }
    .star.filled { color: #ffc107; }
    .rating-text { font-size: 1.1rem; font-weight: 500; }
    .total { color: rgba(255,255,255,0.5); font-size: 0.9rem; }
    .user-rating { display: block; font-size: 0.8rem; color: rgba(255,255,255,0.6); margin-top: 4px; }
  `]
})
export class StarRatingComponent {
  averageRating = input<number>(0);
  totalRatings = input<number>(0);
  userRating = input<number | null>(null);
  readonly = input<boolean>(false);
  ratingSelected = output<number>();

  hoverRating = signal<number>(0);

  displayAverage(): string {
    return (this.averageRating() || 0).toFixed(1);
  }

  isStarFilled(index: number): boolean {
    const rating = this.hoverRating() || this.averageRating() || 0;
    return index <= Math.round(rating);
  }

  onStarHover(index: number): void {
    if (!this.readonly()) this.hoverRating.set(index);
  }

  onStarLeave(): void {
    this.hoverRating.set(0);
  }

  onStarClick(index: number): void {
    if (!this.readonly()) this.ratingSelected.emit(index);
  }
}
