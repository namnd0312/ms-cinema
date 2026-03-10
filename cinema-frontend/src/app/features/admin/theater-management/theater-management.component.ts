import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Theater } from '../../../core/models/movie.model';

@Component({
  selector: 'app-theater-management',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="admin-container">
      <h1>Theater Management</h1>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else {
        @for (theater of theaters(); track theater.id) {
          <mat-card class="item-card">
            <mat-card-content>
              <strong>{{ theater.name }}</strong>
              <p>Seats: {{ theater.totalSeats }}</p>
            </mat-card-content>
          </mat-card>
        } @empty {
          <p class="empty">No theaters found.</p>
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
export class TheaterManagementComponent implements OnInit {
  private http = inject(HttpClient);
  theaters = signal<Theater[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<Theater[]>('/api/theaters').subscribe({
      next: (t) => { this.theaters.set(t); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
