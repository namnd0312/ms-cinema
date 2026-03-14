import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Payment, PaymentIntentRequest, PaymentIntentResponse } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private http = inject(HttpClient);

  createPaymentIntent(request: PaymentIntentRequest): Observable<PaymentIntentResponse> {
    return this.http.post<PaymentIntentResponse>('/api/payments/create-intent', request);
  }

  fakePaymentSuccess(bookingId: number): Observable<any> {
    return this.http.post('/api/payments/fake-success', { bookingId });
  }

  confirmPayment(id: number): Observable<Payment> {
    return this.http.post<Payment>(`/api/payments/${id}/confirm`, {});
  }

  getPayment(id: number): Observable<Payment> {
    return this.http.get<Payment>(`/api/payments/${id}`);
  }

  getMyPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>('/api/payments/my');
  }

  refundPayment(id: number): Observable<any> {
    return this.http.post(`/api/payments/${id}/refund`, {});
  }

  getAllPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>('/api/payments');
  }
}
