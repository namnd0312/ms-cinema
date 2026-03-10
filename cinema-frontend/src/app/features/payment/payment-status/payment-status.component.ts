import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-payment-status',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, RouterLink],
  template: `
    <div class="status-container">
      <mat-card class="status-card">
        <mat-card-content class="center">
          @if (isSuccess()) {
            <mat-icon class="success-icon">check_circle</mat-icon>
            <h2>Payment Successful!</h2>
            <p>Your booking #{{ bookingId() }} has been confirmed.</p>
            <div class="actions">
              <a mat-raised-button color="primary" [routerLink]="['/booking', bookingId()]">View Booking</a>
              <a mat-button routerLink="/movies">Browse Movies</a>
            </div>
          } @else {
            <mat-icon class="error-icon">error</mat-icon>
            <h2>Payment Failed</h2>
            <p>Something went wrong with your payment. Please try again.</p>
            <div class="actions">
              <a mat-raised-button color="primary" [routerLink]="['/payment/pay', bookingId()]">Try Again</a>
              <a mat-button [routerLink]="['/booking', bookingId()]">View Booking</a>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .status-container { display: flex; justify-content: center; align-items: center; min-height: 60vh; padding: 24px; }
    .status-card { max-width: 450px; width: 100%; }
    .center { text-align: center; padding: 32px 16px; }
    .success-icon { font-size: 72px; height: 72px; width: 72px; color: #4caf50; }
    .error-icon { font-size: 72px; height: 72px; width: 72px; color: #f44336; }
    h2 { margin: 16px 0 8px; }
    p { color: rgba(255,255,255,0.7); margin-bottom: 24px; }
    .actions { display: flex; flex-direction: column; gap: 8px; }
  `]
})
export class PaymentStatusComponent implements OnInit {
  private route = inject(ActivatedRoute);
  isSuccess = signal(false);
  bookingId = signal(0);

  ngOnInit(): void {
    this.bookingId.set(Number(this.route.snapshot.paramMap.get('bookingId')));
    const params = this.route.snapshot.queryParams;
    // Handle both manual status param and Stripe redirect_status param
    const status = params['status'] || params['redirect_status'];
    this.isSuccess.set(status === 'success' || status === 'succeeded');
  }
}
