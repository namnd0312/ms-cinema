package com.namnd.paymentservice.service.impl;

import com.namnd.paymentservice.dto.PaymentHistoryResponse;
import com.namnd.paymentservice.dto.PaymentIntentResponse;
import com.namnd.paymentservice.event.PaymentCompletedSpringEvent;
import com.namnd.paymentservice.event.PaymentFailedSpringEvent;
import com.namnd.paymentservice.exception.PaymentException;
import com.namnd.paymentservice.model.Payment;
import com.namnd.paymentservice.model.PaymentStatus;
import com.namnd.paymentservice.repository.PaymentRepository;
import com.namnd.paymentservice.service.PaymentService;
import com.namnd.kafka.events.audit.Auditable;
import com.namnd.kafka.events.domain.AuditAction;
import com.stripe.exception.StripeException;
import io.micrometer.core.instrument.Counter;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.net.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stripe-backed implementation of PaymentService.
 * Handles PaymentIntent creation with idempotency keys,
 * webhook event processing with deduplication, and refunds.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter paymentInitiatedCounter;
    private final Counter paymentCompletedCounter;
    private final Counter paymentFailedCounter;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                               ApplicationEventPublisher eventPublisher,
                               Counter paymentInitiatedCounter,
                               Counter paymentCompletedCounter,
                               Counter paymentFailedCounter) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.paymentInitiatedCounter = paymentInitiatedCounter;
        this.paymentCompletedCounter = paymentCompletedCounter;
        this.paymentFailedCounter = paymentFailedCounter;
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.CREATE, entityType = "Payment")
    public PaymentIntentResponse createPaymentIntent(Long bookingId, Long amount, Long userId) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency("vnd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true).build())
                    .putMetadata("bookingId", bookingId.toString())
                    .putMetadata("userId", userId.toString())
                    .build();

            RequestOptions opts = RequestOptions.builder()
                    .setIdempotencyKey("pay-" + bookingId)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, opts);

            Payment payment = new Payment();
            payment.setBookingId(bookingId);
            payment.setUserId(userId);
            payment.setAmount(amount);
            payment.setCurrency("VND");
            payment.setStatus(PaymentStatus.PENDING);
            payment.setStripePaymentIntentId(intent.getId());
            paymentRepository.save(payment);
            paymentInitiatedCounter.increment();

            return new PaymentIntentResponse(payment.getId(), intent.getClientSecret(), "PENDING");
        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent for bookingId={}: {}", bookingId, e.getMessage(), e);
            throw new PaymentException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentIntentResponse fakePaymentSuccess(Long bookingId, Long amount, Long userId) {
        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setUserId(userId);
        payment.setAmount(amount);
        payment.setCurrency("VND");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setStripePaymentIntentId("FAKE-" + bookingId + "-" + System.currentTimeMillis());
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);
        paymentInitiatedCounter.increment();
        paymentCompletedCounter.increment();

        // Kafka event fires after TX commit via PaymentAfterCommitListener
        eventPublisher.publishEvent(new PaymentCompletedSpringEvent(
                bookingId, payment.getStripePaymentIntentId(), payment.getAmount(), payment.getCurrency()));

        return new PaymentIntentResponse(payment.getId(), null, "COMPLETED");
    }

    @Override
    @Transactional
    public void handleWebhookEvent(Event event) {
        String type = event.getType();
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new PaymentException("Could not deserialize Stripe event object"));

        String piId = intent.getId();
        Payment payment = paymentRepository.findByStripePaymentIntentId(piId)
                .orElseThrow(() -> new PaymentException("Payment not found for PaymentIntent: " + piId));

        // Idempotency: skip if already processed
        if (payment.getStripeEventId() != null) {
            log.info("Skipping duplicate webhook event={} for paymentId={}", event.getId(), payment.getId());
            return;
        }
        payment.setStripeEventId(event.getId());

        if ("payment_intent.succeeded".equals(type)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            paymentCompletedCounter.increment();
            eventPublisher.publishEvent(new PaymentCompletedSpringEvent(
                    payment.getBookingId(), payment.getStripePaymentIntentId(), payment.getAmount(), payment.getCurrency()));
        } else if ("payment_intent.payment_failed".equals(type)) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            paymentFailedCounter.increment();
            eventPublisher.publishEvent(new PaymentFailedSpringEvent(payment.getBookingId(), "Payment failed"));
        } else {
            log.debug("Unhandled webhook event type: {}", type);
        }
    }

    @Override
    @Transactional
    public PaymentHistoryResponse confirmPaymentStatus(Long paymentId, Long userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentException("Access denied: payment does not belong to user");
        }

        // If already completed/failed, return as-is
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return toResponse(payment);
        }

        // Check Stripe directly for the latest status
        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getStripePaymentIntentId());
            String stripeStatus = intent.getStatus();

            if ("succeeded".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepository.save(payment);
                paymentCompletedCounter.increment();
                eventPublisher.publishEvent(new PaymentCompletedSpringEvent(
                        payment.getBookingId(), payment.getStripePaymentIntentId(), payment.getAmount(), payment.getCurrency()));
            } else if ("canceled".equals(stripeStatus) || "requires_payment_method".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                paymentFailedCounter.increment();
                eventPublisher.publishEvent(new PaymentFailedSpringEvent(payment.getBookingId(), "Payment failed"));
            }
            // else still processing — return current status
        } catch (StripeException e) {
            log.error("Stripe error confirming paymentId={}: {}", paymentId, e.getMessage(), e);
            throw new PaymentException("Failed to confirm payment status: " + e.getMessage(), e);
        }

        return toResponse(payment);
    }

    @Override
    @Transactional
    public void refund(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build();
            RequestOptions opts = RequestOptions.builder()
                    .setIdempotencyKey("refund-" + payment.getBookingId())
                    .build();
            Refund.create(params, opts);
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
        } catch (StripeException e) {
            log.error("Stripe error refunding paymentId={}: {}", paymentId, e.getMessage(), e);
            throw new PaymentException("Failed to issue refund: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryResponse getPayment(Long paymentId, Long userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentException("Access denied: payment does not belong to user");
        }
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getUserPayments(Long userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaymentHistoryResponse toResponse(Payment p) {
        return new PaymentHistoryResponse(
                p.getId(),
                p.getBookingId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getPaidAt(),
                p.getCreatedAt()
        );
    }
}
