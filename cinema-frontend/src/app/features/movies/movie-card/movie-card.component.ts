import { Component, input, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Movie } from '../../../core/models/movie.model';

@Component({
  selector: 'app-movie-card',
  standalone: true,
  imports: [MatCardModule, MatChipsModule, MatIconModule],
  template: `
    <mat-card class="movie-card" (click)="selectMovie.emit(movie())">
      <img mat-card-image [src]="movie().posterUrl || 'https://via.placeholder.com/300x450?text=No+Poster'"
           [alt]="movie().title" class="poster">
      <mat-card-content>
        <h3 class="title">{{ movie().title }}</h3>
        <div class="meta">
          <mat-chip-set>
            <mat-chip>{{ movie().genre }}</mat-chip>
          </mat-chip-set>
          <span class="rating">
            <mat-icon class="star-icon">star</mat-icon>
            {{ movie().rating }}/10
          </span>
        </div>
        <p class="duration">{{ movie().durationMinutes }} min</p>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .movie-card { cursor: pointer; transition: transform 0.2s, box-shadow 0.2s; height: 100%; }
    .movie-card:hover { transform: scale(1.03); box-shadow: 0 8px 24px rgba(0,0,0,0.3); }
    .poster { height: 280px; object-fit: cover; }
    .title { margin: 8px 0 4px; font-size: 1.1rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .meta { display: flex; align-items: center; justify-content: space-between; margin: 4px 0; }
    .rating { display: flex; align-items: center; gap: 4px; font-weight: 500; }
    .star-icon { color: #ffc107; font-size: 18px; height: 18px; width: 18px; }
    .duration { color: rgba(255,255,255,0.7); font-size: 0.85rem; margin: 4px 0 0; }
  `]
})
export class MovieCardComponent {
  movie = input.required<Movie>();
  selectMovie = output<Movie>();
}
