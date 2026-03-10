import { Component, input, computed } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Seat, Showtime } from '../../../core/models/movie.model';

@Component({
  selector: 'app-booking-summary',
  standalone: true,
  imports: [CurrencyPipe, MatChipsModule, MatIconModule],
  template: `
    <div class="summary">
      @if (showtime()) {
        <h3>{{ showtime()!.movie.title }}</h3>
        <p class="detail">{{ showtime()!.theater.name }}</p>
      }
      <h4>Selected Seats</h4>
      <mat-chip-set>
        @for (seat of selectedSeats(); track seat.id) {
          <mat-chip>
            <mat-icon matChipAvatar>event_seat</mat-icon>
            {{ seat.seatLabel }} ({{ seat.seatType }}) - {{ seat.price | currency }}
          </mat-chip>
        }
      </mat-chip-set>
      <div class="total">
        <span>Total:</span>
        <span class="amount">{{ totalPrice() | currency }}</span>
      </div>
    </div>
  `,
  styles: [`
    .summary { padding: 16px; }
    h3 { margin: 0 0 4px; }
    .detail { color: rgba(255,255,255,0.6); margin: 0 0 16px; }
    h4 { margin: 16px 0 8px; }
    .total { display: flex; justify-content: space-between; margin-top: 24px;
      padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.1); font-size: 1.2rem; }
    .amount { font-weight: 700; color: #ffc107; }
  `]
})
export class BookingSummaryComponent {
  selectedSeats = input.required<Seat[]>();
  showtime = input<Showtime | null>(null);

  totalPrice = computed(() => this.selectedSeats().reduce((sum, s) => sum + s.price, 0));
}
