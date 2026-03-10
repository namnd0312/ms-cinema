import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BookingService } from '../../../core/services/booking.service';
import { Booking } from '../../../core/models/booking.model';

@Component({
  selector: 'app-booking-history',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, MatCardModule, MatChipsModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="history-container">
      <h1>My Bookings</h1>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else if (bookings().length === 0) {
        <p class="empty">No bookings yet.</p>
      } @else {
        @for (booking of bookings(); track booking.id) {
          <mat-card class="booking-card" (click)="viewDetail(booking.id)">
            <mat-card-content>
              <div class="booking-row">
                <div>
                  <strong>Booking #{{ booking.id }}</strong>
                  <p>{{ booking.reservedAt | date:'MMM d, y h:mm a' }}</p>
                  <p>{{ booking.seats.length }} seat(s)</p>
                </div>
                <div class="right">
                  <mat-chip [class]="'status-' + booking.status.toLowerCase()">
                    {{ booking.status }}
                  </mat-chip>
                  <span class="amount">{{ booking.totalAmount | currency }}</span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>
        }
      }
    </div>
  `,
  styles: [`
    .history-container { padding: 24px; max-width: 800px; margin: 0 auto; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .empty { text-align: center; color: rgba(255,255,255,0.5); padding: 32px; }
    .booking-card { margin-bottom: 12px; cursor: pointer; transition: transform 0.2s; }
    .booking-card:hover { transform: translateX(4px); }
    .booking-row { display: flex; justify-content: space-between; align-items: center; }
    .booking-row p { margin: 4px 0; color: rgba(255,255,255,0.6); font-size: 0.9rem; }
    .right { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
    .amount { font-weight: 600; font-size: 1.1rem; color: #ffc107; }
    :host ::ng-deep .status-reserved { background: #f57f17 !important; color: white !important; }
    :host ::ng-deep .status-confirmed { background: #2e7d32 !important; color: white !important; }
    :host ::ng-deep .status-cancelled { background: #c62828 !important; color: white !important; }
    :host ::ng-deep .status-expired { background: #616161 !important; color: white !important; }
  `]
})
export class BookingHistoryComponent implements OnInit {
  private bookingService = inject(BookingService);
  private router = inject(Router);
  bookings = signal<Booking[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.bookingService.getMyBookings().subscribe({
      next: (b) => { this.bookings.set(b); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  viewDetail(id: number): void {
    this.router.navigate(['/booking', id]);
  }
}
