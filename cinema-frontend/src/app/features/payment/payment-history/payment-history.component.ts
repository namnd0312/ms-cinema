import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PaymentService } from '../../../core/services/payment.service';
import { Payment } from '../../../core/models/payment.model';

@Component({
  selector: 'app-payment-history',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, MatCardModule, MatChipsModule, MatProgressSpinnerModule],
  template: `
    <div class="history-container">
      <h1>Payment History</h1>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else if (payments().length === 0) {
        <p class="empty">No payments yet.</p>
      } @else {
        @for (payment of payments(); track payment.id) {
          <mat-card class="payment-card">
            <mat-card-content>
              <div class="payment-row">
                <div>
                  <strong>Payment #{{ payment.id }}</strong>
                  <p>Booking #{{ payment.bookingId }}</p>
                  <p>{{ payment.createdAt | date:'MMM d, y h:mm a' }}</p>
                </div>
                <div class="right">
                  <mat-chip [class]="'status-' + payment.status.toLowerCase()">
                    {{ payment.status }}
                  </mat-chip>
                  <span class="amount">{{ payment.amount | currency }}</span>
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
    .payment-card { margin-bottom: 12px; }
    .payment-row { display: flex; justify-content: space-between; align-items: center; }
    .payment-row p { margin: 4px 0; color: rgba(255,255,255,0.6); font-size: 0.9rem; }
    .right { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
    .amount { font-weight: 600; font-size: 1.1rem; color: #ffc107; }
    :host ::ng-deep .status-completed { background: #2e7d32 !important; color: white !important; }
    :host ::ng-deep .status-pending { background: #f57f17 !important; color: white !important; }
    :host ::ng-deep .status-failed { background: #c62828 !important; color: white !important; }
    :host ::ng-deep .status-refunded { background: #616161 !important; color: white !important; }
  `]
})
export class PaymentHistoryComponent implements OnInit {
  private paymentService = inject(PaymentService);
  payments = signal<Payment[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.paymentService.getMyPayments().subscribe({
      next: (p) => { this.payments.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
