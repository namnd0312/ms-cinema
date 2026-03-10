import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MovieService } from '../../../core/services/movie.service';
import { Showtime } from '../../../core/models/movie.model';

@Component({
  selector: 'app-showtime-management',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, MatCardModule, MatProgressSpinnerModule],
  template: `
    <div class="admin-container">
      <h1>Showtime Management</h1>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        @for (st of showtimes(); track st.id) {
          <mat-card class="item-card">
            <mat-card-content>
              <strong>{{ st.movie.title }}</strong> &#64; {{ st.theater.name }}
              <p>{{ st.startTime | date:'MMM d, y h:mm a' }} — {{ st.basePrice | currency }}</p>
            </mat-card-content>
          </mat-card>
        } @empty {
          <p class="empty">No showtimes found.</p>
        }
      }
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 800px; margin: 0 auto; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .item-card { margin-bottom: 12px; }
    .empty { text-align: center; color: rgba(255,255,255,0.5); }
  `]
})
export class ShowtimeManagementComponent implements OnInit {
  private movieService = inject(MovieService);
  showtimes = signal<Showtime[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.movieService.getShowtimes().subscribe({
      next: (s) => { this.showtimes.set(s); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
