import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MovieCardComponent } from '../movie-card/movie-card.component';
import { MovieService } from '../../../core/services/movie.service';
import { Movie } from '../../../core/models/movie.model';

@Component({
  selector: 'app-movie-list',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatIconModule,
    MatProgressSpinnerModule, MovieCardComponent],
  template: `
    <div class="movie-list-container">
      <div class="header">
        <h1>Movies</h1>
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Search movies</mat-label>
          <input matInput [ngModel]="searchTerm()" (ngModelChange)="searchTerm.set($event)" placeholder="Search by title...">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>
      </div>

      @if (loading()) {
        <div class="loading">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else {
        <div class="movie-grid">
          @for (movie of filteredMovies(); track movie.id) {
            <app-movie-card [movie]="movie" (selectMovie)="onSelectMovie($event)" />
          } @empty {
            <p class="no-results">No movies found.</p>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .movie-list-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; }
    .search-field { min-width: 250px; }
    .movie-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 20px; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .no-results { text-align: center; padding: 32px; color: rgba(255,255,255,0.5); grid-column: 1 / -1; }
    h1 { margin: 0; }
  `]
})
export class MovieListComponent implements OnInit {
  private movieService = inject(MovieService);
  private router = inject(Router);

  movies = signal<Movie[]>([]);
  loading = signal(true);
  searchTerm = signal('');

  filteredMovies = computed(() => {
    const term = this.searchTerm().toLowerCase();
    if (!term) return this.movies();
    return this.movies().filter(m =>
      m.title.toLowerCase().includes(term) || m.genre?.toLowerCase().includes(term)
    );
  });

  ngOnInit(): void {
    this.movieService.getMovies().subscribe({
      next: (movies) => { this.movies.set(movies); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onSelectMovie(movie: Movie): void {
    this.router.navigate(['/movies', movie.id]);
  }
}
