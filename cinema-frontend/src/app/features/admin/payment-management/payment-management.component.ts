import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PaymentService } from '../../../core/services/payment.service';
import { Payment } from '../../../core/models/payment.model';

@Component({
  selector: 'app-payment-management',
  standalone: true,
  imports: [
    DatePipe, CurrencyPipe,
    MatTableModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatChipsModule
  ],
  template: `
    <div class="admin-container">
      <h1>Payment Management</h1>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <table mat-table [dataSource]="payments()" class="full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>ID</th>
            <td mat-cell *matCellDef="let p">{{p.id}}</td></ng-container>
          <ng-container matColumnDef="bookingId">
            <th mat-header-cell *matHeaderCellDef>Booking</th>
            <td mat-cell *matCellDef="let p">#{{p.bookingId}}</td></ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let p">{{p.amount | currency}}</td></ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let p">
              <mat-chip [class]="'status-' + p.status.toLowerCase()">{{p.status}}</mat-chip>
            </td></ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let p">{{p.createdAt | date:'medium'}}</td></ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let p">
              @if (p.status === 'COMPLETED') {
                <button mat-flat-button color="warn" (click)="refund(p)">Refund</button>
              }
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
      }
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 1000px; margin: 0 auto; }
    .loading { display: flex; justify-content: center; padding: 64px; }
    .full-width { width: 100%; }
    .error { text-align: center; color: #f44336; padding: 32px; }
    .status-completed { background: #4caf50 !important; color: white; }
    .status-refunded { background: #ff9800 !important; color: white; }
    .status-failed { background: #f44336 !important; color: white; }
  `]
})
export class PaymentManagementComponent implements OnInit {
  private paymentService = inject(PaymentService);
  private snackBar = inject(MatSnackBar);
  payments = signal<Payment[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  columns = ['id', 'bookingId', 'amount', 'status', 'createdAt', 'actions'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.paymentService.getAllPayments().subscribe({
      next: (p) => { this.payments.set(p); this.loading.set(false); },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 404
          ? 'Admin payment listing not available. Backend endpoint required.'
          : 'Failed to load payments.');
      }
    });
  }

  refund(payment: Payment): void {
    if (!window.confirm(`Refund payment #${payment.id} ($${payment.amount})?`)) return;
    this.paymentService.refundPayment(payment.id).subscribe({
      next: () => { this.snackBar.open('Payment refunded', 'OK', { duration: 3000 }); this.load(); },
      error: () => this.snackBar.open('Refund failed', 'OK', { duration: 3000 })
    });
  }
}
