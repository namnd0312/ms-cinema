import { Component, inject, signal, OnInit, OnDestroy, ElementRef, viewChild, AfterViewInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { loadStripe, Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';
import { BookingService } from '../../../core/services/booking.service';
import { PaymentService } from '../../../core/services/payment.service';
import { Booking } from '../../../core/models/booking.model';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-payment-page',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, MatCardModule, MatButtonModule, MatProgressSpinnerModule,
    MatChipsModule, MatDividerModule],
  template: `
    @if (loadingBooking()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (booking()) {
      <div class="payment-container">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Payment</mat-card-title>
            <mat-card-subtitle>Booking #{{ booking()!.id }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <h3>Booking Summary</h3>
            <p><strong>Reserved:</strong> {{ booking()!.reservedAt | date:'MMM d, y h:mm a' }}</p>
            <p><strong>Expires:</strong> {{ booking()!.expiresAt | date:'MMM d, y h:mm a' }}</p>

            <h4>Seats</h4>
            <mat-chip-set>
              @for (seat of booking()!.seats; track seat.id) {
                <mat-chip>{{ seat.seatLabel }} - {{ seat.price | currency }}</mat-chip>
              }
            </mat-chip-set>

            <mat-divider></mat-divider>

            <div class="total">
              <span>Total Amount:</span>
              <span class="amount">{{ booking()!.totalAmount | currency }}</span>
            </div>

            @if (clientSecretReady()) {
              <div class="stripe-element-container">
                <div #paymentElement></div>
              </div>
            } @else if (!loadingBooking()) {
              <div class="loading-stripe">
                <mat-spinner diameter="24"></mat-spinner>
                <span>Loading payment form...</span>
              </div>
            }
            @if (stripeError()) {
              <p class="stripe-error">{{ stripeError() }}</p>
            }
          </mat-card-content>
          <mat-card-actions>
            @if (processing()) {
              <div class="processing">
                <mat-spinner diameter="30"></mat-spinner>
                <p>Processing payment...</p>
              </div>
            } @else if (stripeReady()) {
              <button mat-raised-button color="primary" class="pay-btn" (click)="pay()">
                Pay {{ booking()!.totalAmount | currency }}
              </button>
              <button mat-button (click)="cancel()">Cancel</button>
            }
          </mat-card-actions>
        </mat-card>
      </div>
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 64px; }
    .payment-container { padding: 24px; max-width: 500px; margin: 0 auto; }
    h3, h4 { margin: 16px 0 8px; }
    mat-divider { margin: 16px 0; }
    .total { display: flex; justify-content: space-between; font-size: 1.3rem; margin-top: 16px; }
    .amount { font-weight: 700; color: #ffc107; }
    .stripe-element-container { margin-top: 24px; padding: 16px; background: #1e1e2f; border-radius: 8px; }
    .stripe-error { color: #f44336; margin-top: 8px; font-size: 0.9rem; }
    .loading-stripe { display: flex; align-items: center; gap: 12px; margin-top: 24px; color: #aaa; }
    .pay-btn { width: 100%; font-size: 1.1rem; padding: 8px; }
    .processing { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 16px; }
  `]
})
export class PaymentPageComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private bookingService = inject(BookingService);
  private paymentService = inject(PaymentService);
  private snackBar = inject(MatSnackBar);

  paymentElementRef = viewChild<ElementRef>('paymentElement');

  booking = signal<Booking | null>(null);
  loadingBooking = signal(true);
  processing = signal(false);
  clientSecretReady = signal(false);
  stripeReady = signal(false);
  stripeError = signal<string | null>(null);

  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;
  private paymentElement: StripePaymentElement | null = null;
  private clientSecret: string | null = null;
  private paymentId: number | null = null;

  ngOnInit(): void {
    const bookingId = Number(this.route.snapshot.paramMap.get('bookingId'));
    this.bookingService.getBooking(bookingId).subscribe({
      next: (b) => {
        this.booking.set(b);
        this.loadingBooking.set(false);
        this.initStripe(b);
      },
      error: () => this.loadingBooking.set(false)
    });
  }

  ngOnDestroy(): void {
    this.paymentElement?.destroy();
  }

  private async initStripe(booking: Booking): Promise<void> {
    this.stripe = await loadStripe(environment.stripePublishableKey);
    if (!this.stripe) {
      this.stripeError.set('Failed to load Stripe. Please refresh.');
      return;
    }

    this.paymentService.createPaymentIntent({
      bookingId: booking.id,
      amount: booking.totalAmount
    }).subscribe({
      next: (res) => {
        this.clientSecret = res.clientSecret;
        this.paymentId = res.paymentId;
        this.mountPaymentElement();
      },
      error: () => {
        this.stripeError.set('Failed to initialize payment. Please try again.');
      }
    });
  }

  private mountPaymentElement(): void {
    if (!this.stripe || !this.clientSecret) return;

    this.elements = this.stripe.elements({
      clientSecret: this.clientSecret,
      appearance: {
        theme: 'night',
        variables: {
          colorPrimary: '#6366f1',
          colorBackground: '#1e1e2f',
          colorText: '#ffffff',
          colorDanger: '#f44336',
          fontFamily: 'Roboto, sans-serif',
          borderRadius: '8px'
        }
      }
    });

    this.paymentElement = this.elements.create('payment');

    // Set clientSecretReady to render the #paymentElement div, then mount in next tick
    this.clientSecretReady.set(true);
    setTimeout(() => {
      const ref = this.paymentElementRef();
      if (ref) {
        this.paymentElement!.mount(ref.nativeElement);
        this.paymentElement!.on('ready', () => this.stripeReady.set(true));
      }
    });
  }

  async pay(): Promise<void> {
    if (!this.stripe || !this.elements) return;

    this.processing.set(true);
    this.stripeError.set(null);

    const { error } = await this.stripe.confirmPayment({
      elements: this.elements,
      confirmParams: {
        return_url: `${window.location.origin}/payment/status/${this.booking()!.id}`
      },
      redirect: 'if_required'
    });

    if (error) {
      this.processing.set(false);
      this.stripeError.set(error.message ?? 'Payment failed. Please try again.');
      this.snackBar.open(error.message ?? 'Payment failed', 'OK', { duration: 5000 });
    } else {
      // Tell backend to check Stripe and update status from PENDING → COMPLETED
      this.paymentService.confirmPayment(this.paymentId!).subscribe({
        next: () => {
          this.snackBar.open('Payment successful!', 'OK', { duration: 3000 });
          this.router.navigate(['/payment/status', this.booking()!.id],
            { queryParams: { status: 'success' } });
        },
        error: () => {
          this.snackBar.open('Payment successful!', 'OK', { duration: 3000 });
          this.router.navigate(['/payment/status', this.booking()!.id],
            { queryParams: { status: 'success' } });
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/booking', this.booking()!.id]);
  }
}
