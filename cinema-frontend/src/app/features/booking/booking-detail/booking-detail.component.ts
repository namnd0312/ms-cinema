import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { BookingService } from '../../../core/services/booking.service';
import { Booking } from '../../../core/models/booking.model';

@Component({
  selector: 'app-booking-detail',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, MatCardModule, MatButtonModule, MatChipsModule,
    MatProgressSpinnerModule, MatDividerModule],
  template: `
    @if (loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (booking()) {
      <div class="detail-container">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Booking #{{ booking()!.id }}</mat-card-title>
            <mat-chip [class]="'status-' + booking()!.status.toLowerCase()">
              {{ booking()!.status }}
            </mat-chip>
          </mat-card-header>
          <mat-card-content>
            <p><strong>Reserved:</strong> {{ booking()!.reservedAt | date:'MMM d, y h:mm a' }}</p>
            @if (booking()!.confirmedAt) {
              <p><strong>Confirmed:</strong> {{ booking()!.confirmedAt | date:'MMM d, y h:mm a' }}</p>
            }
            <p><strong>Expires:</strong> {{ booking()!.expiresAt | date:'MMM d, y h:mm a' }}</p>

            <mat-divider></mat-divider>

            <h3>Seats</h3>
            <mat-chip-set>
              @for (seat of booking()!.seats; track seat.id) {
                <mat-chip>{{ seat.seatLabel }} ({{ seat.seatType }}) - {{ seat.price | currency }}</mat-chip>
              }
            </mat-chip-set>

            <div class="total">
              <span>Total:</span>
              <span class="amount">{{ booking()!.totalAmount | currency }}</span>
            </div>
          </mat-card-content>
          <mat-card-actions>
            @if (booking()!.status === 'RESERVED') {
              <button mat-raised-button color="primary"
                (click)="goToPayment()">Pay Now</button>
              <button mat-button color="warn"
                (click)="cancel()">Cancel Booking</button>
            }
            @if (booking()!.status === 'CONFIRMED') {
              <button mat-button color="warn"
                (click)="cancel()">Cancel Booking</button>
            }
            <button mat-button (click)="goBack()">Back to History</button>
          </mat-card-actions>
        </mat-card>
      </div>
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 64px; }
    .detail-container { padding: 24px; max-width: 600px; margin: 0 auto; }
    mat-card-header { display: flex; justify-content: space-between; align-items: center; }
    h3 { margin: 16px 0 8px; }
    .total { display: flex; justify-content: space-between; margin-top: 24px; padding-top: 16px;
      border-top: 1px solid rgba(255,255,255,0.1); font-size: 1.2rem; }
    .amount { font-weight: 700; color: #ffc107; }
    :host ::ng-deep .status-reserved { background: #f57f17 !important; color: white !important; }
    :host ::ng-deep .status-confirmed { background: #2e7d32 !important; color: white !important; }
    :host ::ng-deep .status-cancelled { background: #c62828 !important; color: white !important; }
    :host ::ng-deep .status-expired { background: #616161 !important; color: white !important; }
  `]
})
export class BookingDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private bookingService = inject(BookingService);
  private snackBar = inject(MatSnackBar);
  booking = signal<Booking | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.bookingService.getBooking(id).subscribe({
      next: (b) => { this.booking.set(b); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  cancel(): void {
    this.bookingService.cancelBooking(this.booking()!.id).subscribe({
      next: () => {
        this.snackBar.open('Booking cancelled', 'OK', { duration: 3000 });
        this.booking.update(b => b ? { ...b, status: 'CANCELLED' } : null);
      }
    });
  }

  goToPayment(): void {
    this.router.navigate(['/payment/pay', this.booking()!.id]);
  }

  goBack(): void {
    this.router.navigate(['/booking/history']);
  }
}
