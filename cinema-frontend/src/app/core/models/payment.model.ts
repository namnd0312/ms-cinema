export interface Payment {
  id: number;
  bookingId: number;
  userId: number;
  amount: number;
  currency: string;
  status: string; // PENDING, COMPLETED, FAILED, REFUNDED
  stripePaymentIntentId: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface PaymentIntentRequest {
  bookingId: number;
  amount: number;
}

export interface PaymentIntentResponse {
  paymentId: number;
  clientSecret: string;
  status: string;
}
