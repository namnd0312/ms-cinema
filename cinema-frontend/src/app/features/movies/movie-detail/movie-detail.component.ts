import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MovieService } from '../../../core/services/movie.service';
import { MovieRatingService } from '../../../core/services/movie-rating.service';
import { AuthService } from '../../../core/services/auth.service';
import { Movie, Showtime } from '../../../core/models/movie.model';
import { MovieRatingSummaryDto } from '../../../core/models/movie-rating.model';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { CommentListComponent } from '../comment-list/comment-list.component';

@Component({
  selector: 'app-movie-detail',
  standalone: true,
  imports: [
    DatePipe,
    MatCardModule, MatButtonModule, MatChipsModule, MatIconModule,
    MatProgressSpinnerModule, MatDividerModule, MatSnackBarModule,
    StarRatingComponent, CommentListComponent
  ],
  template: `
    @if (loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (movie()) {
      <div class="detail-container">
        <div class="movie-header">
          <img [src]="movie()!.posterUrl || 'https://via.placeholder.com/300x450?text=No+Poster'"
               [alt]="movie()!.title" class="poster">
          <div class="info">
            <h1>{{ movie()!.title }}</h1>
            <div class="meta">
              <mat-chip-set>
                <mat-chip>{{ movie()!.genre }}</mat-chip>
                <mat-chip>{{ movie()!.durationMinutes }} min</mat-chip>
              </mat-chip-set>
              <div class="rating-wrapper">
                <app-star-rating
                  [averageRating]="ratingSummary()?.averageRating ?? (movie()!.averageRating || 0)"
                  [totalRatings]="ratingSummary()?.totalRatings ?? (movie()!.totalRatings || 0)"
                  [userRating]="ratingSummary()?.userRating ?? null"
                  [readonly]="!isAuthenticated()"
                  (ratingSelected)="onRate($event)"
                />
              </div>
            </div>
            <p class="description">{{ movie()!.description }}</p>
          </div>
        </div>

        <mat-divider></mat-divider>

        <h2>Showtimes</h2>
        @if (showtimes().length === 0) {
          <p class="no-showtimes">No showtimes available.</p>
        } @else {
          <div class="showtimes-grid">
            @for (st of showtimes(); track st.id) {
              <mat-card class="showtime-card">
                <mat-card-content>
                  <div class="showtime-info">
                    <div>
                      <strong>{{ st.startTime | date:'MMM d, y' }}</strong>
                      <p>{{ st.startTime | date:'h:mm a' }} - {{ st.endTime | date:'h:mm a' }}</p>
                      <p class="theater">{{ st.theater.name }}</p>
                    </div>
                    <div class="price-action">
                      <span class="price">\${{ st.basePrice }}</span>
                      <button mat-raised-button color="primary" (click)="bookShowtime(st)">Book Now</button>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>
            }
          </div>
        }

        <mat-divider></mat-divider>
        <app-comment-list [movieId]="movie()!.id" />
      </div>
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 64px; }
    .detail-container { padding: 24px; max-width: 900px; margin: 0 auto; }
    .movie-header { display: flex; gap: 24px; margin-bottom: 24px; }
    .poster { width: 250px; border-radius: 8px; object-fit: cover; }
    .info { flex: 1; }
    .info h1 { margin: 0 0 12px; }
    .meta { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .rating-wrapper { display: flex; flex-direction: column; }
    .description { line-height: 1.6; color: rgba(255,255,255,0.8); }
    .showtimes-grid { display: grid; gap: 12px; margin-top: 16px; }
    .showtime-card { cursor: default; }
    .showtime-info { display: flex; justify-content: space-between; align-items: center; }
    .theater { color: rgba(255,255,255,0.6); margin: 4px 0 0; }
    .price-action { display: flex; flex-direction: column; align-items: center; gap: 8px; }
    .price { font-size: 1.3rem; font-weight: 600; color: #ffc107; }
    .no-showtimes { text-align: center; color: rgba(255,255,255,0.5); }
    mat-divider { margin: 24px 0; }
    @media (max-width: 600px) {
      .movie-header { flex-direction: column; align-items: center; }
      .poster { width: 200px; }
    }
  `]
})
export class MovieDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private movieService = inject(MovieService);
  private movieRatingService = inject(MovieRatingService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  movie = signal<Movie | null>(null);
  showtimes = signal<Showtime[]>([]);
  ratingSummary = signal<MovieRatingSummaryDto | null>(null);
  loading = signal(true);

  isAuthenticated = computed(() => this.authService.isAuthenticated());

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.movieService.getMovie(id).subscribe({
      next: (movie) => {
        this.movie.set(movie);
        this.loadShowtimes(id);
        this.loadRatingSummary(id);
      },
      error: () => this.loading.set(false)
    });
  }

  private loadShowtimes(movieId: number): void {
    this.movieService.getShowtimes(movieId).subscribe({
      next: (showtimes) => { this.showtimes.set(showtimes); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  private loadRatingSummary(movieId: number): void {
    this.movieRatingService.getRatingSummary(movieId).subscribe({
      next: (summary) => this.ratingSummary.set(summary),
      error: () => {} // non-critical, fall back to movie data
    });
  }

  onRate(rating: number): void {
    const movie = this.movie();
    if (!movie) return;
    this.movieRatingService.rateMovie(movie.id, { rating }).subscribe({
      next: () => {
        this.loadRatingSummary(movie.id);
        this.snackBar.open(`Rated ${rating}/5!`, 'Close', { duration: 2000 });
      },
      error: () => this.snackBar.open('Failed to submit rating.', 'Close', { duration: 3000 })
    });
  }

  bookShowtime(showtime: Showtime): void {
    this.router.navigate(['/booking/select', showtime.id]);
  }
}
