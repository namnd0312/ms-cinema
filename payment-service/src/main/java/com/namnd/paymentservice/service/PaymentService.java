package com.namnd.paymentservice.service;

import com.namnd.paymentservice.dto.PaymentHistoryResponse;
import com.namnd.paymentservice.dto.PaymentIntentResponse;
import com.stripe.model.Event;

import java.util.List;

/**
 * Contract for payment operations: creating Stripe PaymentIntents,
 * processing webhook events, issuing refunds, and querying history.
 */
public interface PaymentService {

    PaymentIntentResponse createPaymentIntent(Long bookingId, Long amount, Long userId);

    PaymentIntentResponse fakePaymentSuccess(Long bookingId, Long amount, Long userId);

    void handleWebhookEvent(Event event);

    PaymentHistoryResponse confirmPaymentStatus(Long paymentId, Long userId);

    void refund(Long paymentId);

    PaymentHistoryResponse getPayment(Long paymentId, Long userId);

    List<PaymentHistoryResponse> getUserPayments(Long userId);

    List<PaymentHistoryResponse> getAllPayments();
}
