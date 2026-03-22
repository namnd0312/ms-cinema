import { Component, inject, signal, computed, OnInit, OnDestroy, HostListener } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { forkJoin, Subscription } from 'rxjs';
import { MatStepperModule } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SeatGridComponent } from '../seat-grid/seat-grid.component';
import { BookingSummaryComponent } from '../booking-summary/booking-summary.component';
import { SeatSuggestionPanelComponent } from '../seat-suggestion-panel/seat-suggestion-panel.component';
import { MovieService } from '../../../core/services/movie.service';
import { BookingService } from '../../../core/services/booking.service';
import { SeatWebSocketService } from '../../../core/services/seat-websocket.service';
import { SeatSuggestionService, SeatGroup } from '../../../core/services/seat-suggestion.service';
import { Seat, Showtime } from '../../../core/models/movie.model';
import { Booking } from '../../../core/models/booking.model';
import { BookingCountdownTimer } from './seat-selection-timer.utils';

@Component({
  selector: 'app-seat-selection',
  standalone: true,
  imports: [MatStepperModule, MatButtonModule, MatProgressSpinnerModule, CurrencyPipe,
    SeatGridComponent, BookingSummaryComponent, SeatSuggestionPanelComponent],
  template: `
    @if (loadingSeats()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else {
      <div class="selection-container">
        <mat-stepper linear #stepper>
          <mat-step [completed]="selectedSeatIds().size > 0" label="Select Seats">
            <!-- Suggestion panel -->
            <app-seat-suggestion-panel
              [suggestions]="suggestedGroups()"
              [noResults]="suggestionNoResults()"
              (groupSizeChanged)="onFindSeats($event)"
              (suggestionSelected)="onSuggestionSelected($event)"
              (suggestionsCleared)="clearSuggestions()" />
            <app-seat-grid [seats]="seats()" [selectedSeatIds]="selectedSeatIds()"
              [suggestedSeatIds]="suggestedSeatIds()"
              (seatToggled)="toggleSeat($event)" />
            <div class="step-actions">
              <span class="seat-count">{{ selectedSeatIds().size }} seat(s) selected</span>
              <button mat-raised-button color="primary" matStepperNext
                [disabled]="selectedSeatIds().size === 0">Next</button>
            </div>
          </mat-step>
          <mat-step label="Review">
            <app-booking-summary [selectedSeats]="selectedSeatsArray()" [showtime]="showtime()" />
            @if (countdown()) {
              <p class="countdown">Reservation expires in: {{ countdown() }}</p>
            }
            <div class="step-actions">
              <button mat-button matStepperPrevious>Back</button>
              <button mat-raised-button color="accent" (click)="reserve()"
                [disabled]="reserving()">
                @if (reserving()) { <mat-spinner diameter="20"></mat-spinner> }
                @else { Reserve & Pay }
              </button>
            </div>
          </mat-step>
          <mat-step label="Payment">
            <div class="payment-redirect">
              <p>Booking created! Redirecting to payment...</p>
              <mat-spinner diameter="30"></mat-spinner>
            </div>
          </mat-step>
        </mat-stepper>
      </div>
      @if (isMobile() && selectedSeatIds().size > 0) {
        <div class="mobile-summary-bar">
          <span>{{ selectedSeatIds().size }} seat(s)</span>
          <span class="mobile-total">{{ totalPrice() | currency }}</span>
          <button mat-raised-button color="primary" matStepperNext
            [disabled]="selectedSeatIds().size === 0">Next</button>
        </div>
      }
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 64px; }
    .selection-container { padding: 24px; max-width: 900px; margin: 0 auto; }
    .step-actions { display: flex; justify-content: space-between; align-items: center;
      margin-top: 16px; padding: 16px 0; }
    .seat-count { color: rgba(255,255,255,0.7); }
    .countdown { color: #ff9800; text-align: center; font-weight: 500; }
    .payment-redirect { text-align: center; padding: 32px; }
    .mobile-summary-bar { position: fixed; bottom: 0; left: 0; right: 0; z-index: 100;
      display: flex; align-items: center; justify-content: space-between;
      padding: 12px 16px; background: rgba(30,30,30,0.95);
      border-top: 1px solid rgba(255,255,255,0.1); backdrop-filter: blur(8px);
      font-size: 0.9rem; color: rgba(255,255,255,0.8); }
    .mobile-total { font-weight: 700; color: #ffc107; }
    @media (max-width: 600px) {
      .selection-container { padding: 12px 8px; max-width: 100%; }
      .step-actions { padding: 12px 0; }
    }
  `]
})
export class SeatSelectionComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private movieService = inject(MovieService);
  private bookingService = inject(BookingService);
  private snackBar = inject(MatSnackBar);
  private seatWs = inject(SeatWebSocketService);
  private suggestionService = inject(SeatSuggestionService);
  private timer = new BookingCountdownTimer();
  private wsSub: Subscription | null = null;

  seats = signal<Seat[]>([]);
  showtime = signal<Showtime | null>(null);
  selectedSeatIds = signal<Set<number>>(new Set());
  loadingSeats = signal(true);
  reserving = signal(false);
  booking = signal<Booking | null>(null);
  countdown = signal('');
  isMobile = signal(typeof window !== 'undefined' && window.innerWidth <= 600);

  // Suggestion state
  suggestedGroups = signal<SeatGroup[]>([]);
  suggestionNoResults = signal(false);
  suggestedSeatIds = computed(() => {
    const ids = new Set<number>();
    for (const group of this.suggestedGroups()) {
      for (const seat of group.seats) ids.add(seat.id);
    }
    return ids;
  });

  selectedSeatsArray = computed(() =>
    this.seats().filter(s => this.selectedSeatIds().has(s.id))
  );
  totalPrice = computed(() =>
    this.selectedSeatsArray().reduce((sum, s) => sum + s.price, 0)
  );

  @HostListener('window:resize')
  onResize(): void { this.isMobile.set(window.innerWidth <= 600); }

  ngOnInit(): void {
    const showtimeId = Number(this.route.snapshot.paramMap.get('showtimeId'));
    this.movieService.getShowtime(showtimeId).subscribe({
      next: (st) => this.showtime.set(st)
    });
    forkJoin({
      seats: this.movieService.getShowtimeSeats(showtimeId),
      bookedIds: this.bookingService.getBookedSeatIds(showtimeId)
    }).subscribe({
      next: ({ seats, bookedIds }) => {
        const bookedSet = new Set(bookedIds);
        this.seats.set(seats.map(s => ({
          ...s, status: bookedSet.has(s.id) ? 'OCCUPIED' : 'AVAILABLE'
        })));
        this.loadingSeats.set(false);
        this.subscribeToSeatUpdates(showtimeId);
      },
      error: () => {
        this.loadingSeats.set(false);
        this.snackBar.open('Failed to load seats. Please try again.', 'OK', { duration: 5000 });
      }
    });
  }

  ngOnDestroy(): void {
    this.timer.stop();
    this.wsSub?.unsubscribe();
    this.seatWs.disconnect();
  }

  toggleSeat(seat: Seat): void {
    const current = new Set(this.selectedSeatIds());
    current.has(seat.id) ? current.delete(seat.id) : current.add(seat.id);
    this.selectedSeatIds.set(current);
  }

  reserve(): void {
    const showtimeId = Number(this.route.snapshot.paramMap.get('showtimeId'));
    this.reserving.set(true);
    this.bookingService.reserveSeats({
      showtimeId, seatIds: Array.from(this.selectedSeatIds())
    }).subscribe({
      next: (booking) => {
        this.booking.set(booking);
        this.reserving.set(false);
        this.timer.start(booking.expiresAt, this.countdown);
        this.snackBar.open('Booking reserved! Redirecting to payment...', 'OK', { duration: 3000 });
        setTimeout(() => this.router.navigate(['/payment/pay', booking.id]), 1500);
      },
      error: () => {
        this.reserving.set(false);
        this.snackBar.open('Reservation failed. Please try again.', 'OK', { duration: 5000 });
      }
    });
  }

  // --- Suggestion handlers ---
  onFindSeats(groupSize: number): void {
    const occupied = new Set(
      this.seats().filter(s => s.status === 'OCCUPIED' || s.status === 'RESERVED').map(s => s.id)
    );
    const groups = this.suggestionService.findAdjacentGroups(
      this.seats(), occupied, groupSize
    );
    this.suggestedGroups.set(groups);
    this.suggestionNoResults.set(groups.length === 0);
  }

  onSuggestionSelected(group: SeatGroup): void {
    this.selectedSeatIds.set(new Set(group.seats.map(s => s.id)));
    this.clearSuggestions();
  }

  clearSuggestions(): void {
    this.suggestedGroups.set([]);
    this.suggestionNoResults.set(false);
  }

  private subscribeToSeatUpdates(showtimeId: number): void {
    this.wsSub = this.seatWs.subscribe(showtimeId).subscribe(msg => {
      const seatIdSet = new Set(msg.seatIds);
      const isOccupied = msg.status === 'RESERVED' || msg.status === 'CONFIRMED' || msg.status === 'LOCKED';
      this.seats.update(seats => seats.map(s =>
        seatIdSet.has(s.id) ? { ...s, status: isOccupied ? 'OCCUPIED' : 'AVAILABLE' } : s
      ));
      if (isOccupied) {
        this.selectedSeatIds.update(ids => {
          const next = new Set(ids);
          for (const id of msg.seatIds) next.delete(id);
          return next.size !== ids.size ? next : ids;
        });
      }
    });
  }
}
