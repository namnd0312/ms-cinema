package com.namnd.bookingservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.namnd.bookingservice.model.Booking;
import com.namnd.bookingservice.model.BookingStatus;
import com.namnd.bookingservice.repository.BookingRepository;
import com.namnd.bookingservice.service.BookingService;
import com.namnd.kafka.events.domain.PaymentCompletedEvent;
import com.namnd.kafka.events.domain.PaymentFailedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import com.namnd.bookingservice.service.NotificationPublisherService;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for payment-events topic.
 * Confirms or cancels bookings based on typed payment domain events.
 * Publishes in-app notifications after payment outcomes.
 * Idempotency: skips events for bookings already in a terminal state.
 * Exceptions propagate to trigger DLT retry via DefaultErrorHandler.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;
    private final NotificationPublisherService notificationPublisher;

    public PaymentEventListener(BookingService bookingService,
                                BookingRepository bookingRepository,
                                ObjectMapper objectMapper,
                                NotificationPublisherService notificationPublisher) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
        this.notificationPublisher = notificationPublisher;
    }

    /**
     * Handles payment domain events. Raw EventEnvelope is received because generic
     * type erasure causes JsonDeserializer to deserialize the payload as LinkedHashMap;
     * we convert it to the concrete event type based on eventType discriminator.
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "booking-service")
    public void handlePaymentEvent(EventEnvelope envelope) {
        String eventType = envelope.eventType();
        log.info("Received payment event: type={}, correlationId={}", eventType, envelope.correlationId());

        if ("payment.completed".equals(eventType)) {
            PaymentCompletedEvent event = objectMapper.convertValue(envelope.payload(), PaymentCompletedEvent.class);
            Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();
            if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.CANCELLED) {
                log.info("Booking {} already in terminal state {}, skipping", event.bookingId(), booking.getStatus());
                return;
            }
            bookingService.confirmBooking(event.bookingId());
            log.info("Booking {} confirmed via payment event", event.bookingId());

            notificationPublisher.notifyPaymentSuccess(
                    booking.getUserId(), event.bookingId(), event.paymentId(), event.amount());

        } else if ("payment.failed".equals(eventType)) {
            PaymentFailedEvent event = objectMapper.convertValue(envelope.payload(), PaymentFailedEvent.class);
            Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                log.info("Booking {} already cancelled, skipping", event.bookingId());
                return;
            }
            bookingService.cancelBooking(event.bookingId(), null);
            log.info("Booking {} cancelled due to payment failure: {}", event.bookingId(), event.reason());

            notificationPublisher.notifyPaymentFailed(
                    booking.getUserId(), event.bookingId(), event.reason());

        } else {
            log.warn("Unknown payment event type: {}", eventType);
        }
    }
}
