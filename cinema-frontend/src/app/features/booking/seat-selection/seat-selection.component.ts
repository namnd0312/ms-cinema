import { Component, inject, signal, computed, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MatStepperModule } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SeatGridComponent } from '../seat-grid/seat-grid.component';
import { BookingSummaryComponent } from '../booking-summary/booking-summary.component';
import { MovieService } from '../../../core/services/movie.service';
import { BookingService } from '../../../core/services/booking.service';
import { Seat, Showtime } from '../../../core/models/movie.model';
import { Booking } from '../../../core/models/booking.model';

@Component({
  selector: 'app-seat-selection',
  standalone: true,
  imports: [MatStepperModule, MatButtonModule, MatProgressSpinnerModule,
    SeatGridComponent, BookingSummaryComponent],
  template: `
    @if (loadingSeats()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else {
      <div class="selection-container">
        <mat-stepper linear #stepper>
          <!-- Step 1: Select Seats -->
          <mat-step [completed]="selectedSeatIds().size > 0" label="Select Seats">
            <app-seat-grid [seats]="seats()" [selectedSeatIds]="selectedSeatIds()"
              (seatToggled)="toggleSeat($event)" />
            <div class="step-actions">
              <span class="seat-count">{{ selectedSeatIds().size }} seat(s) selected</span>
              <button mat-raised-button color="primary" matStepperNext
                [disabled]="selectedSeatIds().size === 0">Next</button>
            </div>
          </mat-step>

          <!-- Step 2: Summary -->
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

          <!-- Step 3: Payment redirect -->
          <mat-step label="Payment">
            <div class="payment-redirect">
              <p>Booking created! Redirecting to payment...</p>
              <mat-spinner diameter="30"></mat-spinner>
            </div>
          </mat-step>
        </mat-stepper>
      </div>
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
  `]
})
export class SeatSelectionComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private movieService = inject(MovieService);
  private bookingService = inject(BookingService);
  private snackBar = inject(MatSnackBar);

  seats = signal<Seat[]>([]);
  showtime = signal<Showtime | null>(null);
  selectedSeatIds = signal<Set<number>>(new Set());
  loadingSeats = signal(true);
  reserving = signal(false);
  booking = signal<Booking | null>(null);
  countdown = signal('');
  private countdownInterval: ReturnType<typeof setInterval> | null = null;

  selectedSeatsArray = computed(() =>
    this.seats().filter(s => this.selectedSeatIds().has(s.id))
  );

  ngOnInit(): void {
    const showtimeId = Number(this.route.snapshot.paramMap.get('showtimeId'));
    this.movieService.getShowtime(showtimeId).subscribe({
      next: (st) => this.showtime.set(st)
    });
    // Load seats and booked seat IDs in parallel, then merge status
    forkJoin({
      seats: this.movieService.getShowtimeSeats(showtimeId),
      bookedIds: this.bookingService.getBookedSeatIds(showtimeId)
    }).subscribe({
      next: ({ seats, bookedIds }) => {
        const bookedSet = new Set(bookedIds);
        const seatsWithStatus = seats.map(s => ({
          ...s,
          status: bookedSet.has(s.id) ? 'OCCUPIED' : 'AVAILABLE'
        }));
        this.seats.set(seatsWithStatus);
        this.loadingSeats.set(false);
      },
      error: () => this.loadingSeats.set(false)
    });
  }

  ngOnDestroy(): void {
    if (this.countdownInterval) clearInterval(this.countdownInterval);
  }

  toggleSeat(seat: Seat): void {
    const current = new Set(this.selectedSeatIds());
    if (current.has(seat.id)) {
      current.delete(seat.id);
    } else {
      current.add(seat.id);
    }
    this.selectedSeatIds.set(current);
  }

  reserve(): void {
    const showtimeId = Number(this.route.snapshot.paramMap.get('showtimeId'));
    this.reserving.set(true);
    this.bookingService.reserveSeats({
      showtimeId,
      seatIds: Array.from(this.selectedSeatIds())
    }).subscribe({
      next: (booking) => {
        this.booking.set(booking);
        this.reserving.set(false);
        this.startCountdown(booking.expiresAt);
        this.snackBar.open('Booking reserved! Redirecting to payment...', 'OK', { duration: 3000 });
        // Navigate to payment after short delay
        setTimeout(() => {
          this.router.navigate(['/payment/pay', booking.id]);
        }, 1500);
      },
      error: () => this.reserving.set(false)
    });
  }

  private startCountdown(expiresAt: string): void {
    const updateCountdown = () => {
      const diff = new Date(expiresAt).getTime() - Date.now();
      if (diff <= 0) {
        this.countdown.set('EXPIRED');
        if (this.countdownInterval) clearInterval(this.countdownInterval);
        return;
      }
      const min = Math.floor(diff / 60000);
      const sec = Math.floor((diff % 60000) / 1000);
      this.countdown.set(`${min}:${sec.toString().padStart(2, '0')}`);
    };
    updateCountdown();
    this.countdownInterval = setInterval(updateCountdown, 1000);
  }
}
