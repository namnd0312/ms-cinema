package com.namnd.bookingservice.listener;

import com.namnd.bookingservice.model.Booking;
import com.namnd.bookingservice.model.BookingSeat;
import com.namnd.bookingservice.model.BookingStatus;
import com.namnd.bookingservice.repository.BookingRepository;
import com.namnd.bookingservice.service.SeatLockService;
import com.namnd.kafka.events.domain.PaymentCompletedEvent;
import com.namnd.kafka.events.domain.PaymentFailedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;

/**
 * Integration test for PaymentEventListener using EmbeddedKafka.
 * Verifies: happy path confirmation, payment failure cancellation,
 * idempotent consumption, and DLT routing for malformed messages.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaTopics.PAYMENT_EVENTS, KafkaTopics.PAYMENT_EVENTS + "-dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class PaymentEventListenerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    // Mock external dependencies that need Redis / Feign
    @MockitoBean
    private SeatLockService seatLockService;

    @MockitoBean
    private com.namnd.bookingservice.client.MovieServiceClient movieServiceClient;

    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private Long testBookingId;

    @BeforeEach
    void setUp() {
        doNothing().when(seatLockService).unlockSeats(anyLong(), anyList());

        bookingRepository.deleteAll();

        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setShowtimeId(100L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(200_000L);
        booking.setReservedAt(LocalDateTime.now());
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        BookingSeat seat = new BookingSeat();
        seat.setBooking(booking);
        seat.setShowtimeId(100L);
        seat.setSeatId(1L);
        seat.setSeatLabel("A1");
        seat.setSeatType("STANDARD");
        seat.setPrice(200_000L);
        booking.setSeats(List.of(seat));

        Booking saved = bookingRepository.save(booking);
        testBookingId = saved.getId();
    }

    @Test
    void shouldConfirmBookingOnPaymentCompleted() {
        PaymentCompletedEvent payload = new PaymentCompletedEvent(
                testBookingId, "pi_test_123", 200_000L, "VND");
        EventEnvelope<PaymentCompletedEvent> envelope = EventEnvelope.of(
                "payment-service", "payment.completed", testBookingId.toString(), payload);

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, testBookingId.toString(), envelope);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Booking booking = bookingRepository.findById(testBookingId).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.getConfirmedAt()).isNotNull();
        });
    }

    @Test
    void shouldCancelBookingOnPaymentFailed() {
        PaymentFailedEvent payload = new PaymentFailedEvent(testBookingId, "Card declined");
        EventEnvelope<PaymentFailedEvent> envelope = EventEnvelope.of(
                "payment-service", "payment.failed", testBookingId.toString(), payload);

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, testBookingId.toString(), envelope);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Booking booking = bookingRepository.findById(testBookingId).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        });
    }

    @Test
    void shouldBeIdempotentOnDuplicatePaymentCompleted() {
        // First event: confirm booking
        PaymentCompletedEvent payload = new PaymentCompletedEvent(
                testBookingId, "pi_test_456", 200_000L, "VND");
        EventEnvelope<PaymentCompletedEvent> envelope = EventEnvelope.of(
                "payment-service", "payment.completed", testBookingId.toString(), payload);

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, testBookingId.toString(), envelope);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Booking booking = bookingRepository.findById(testBookingId).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        });

        LocalDateTime firstConfirmedAt = bookingRepository.findById(testBookingId)
                .orElseThrow().getConfirmedAt();

        // Second (duplicate) event: should be skipped
        EventEnvelope<PaymentCompletedEvent> duplicate = EventEnvelope.of(
                "payment-service", "payment.completed", testBookingId.toString(), payload);
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, testBookingId.toString(), duplicate);

        // Wait a bit for the duplicate to be consumed, then verify no state change
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Booking booking = bookingRepository.findById(testBookingId).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.getConfirmedAt()).isEqualTo(firstConfirmedAt);
        });
    }

    @Test
    void shouldRouteToDltAfterRetriesExhausted() {
        // Set up DLT consumer BEFORE sending the event to avoid missing the message
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-test-group", "true", embeddedKafka);
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        Consumer<String, String> dltConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
        embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, KafkaTopics.PAYMENT_EVENTS + "-dlt");

        // Send a valid EventEnvelope referencing a non-existent booking.
        // The listener throws BookingNotFoundException on findById(),
        // triggering retries (3x exponential back-off) then DLT routing.
        Long nonExistentBookingId = 999_999L;
        PaymentCompletedEvent payload = new PaymentCompletedEvent(
                nonExistentBookingId, "pi_bad", 100L, "VND");
        EventEnvelope<PaymentCompletedEvent> envelope = EventEnvelope.of(
                "payment-service", "payment.completed", nonExistentBookingId.toString(), payload);

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, nonExistentBookingId.toString(), envelope);

        // Accumulate records across poll iterations to avoid discarding between retries
        List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> accumulated = new ArrayList<>();

        // Poll DLT topic — retries take ~7s (1s + 2s + 4s), then DLT publish
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
            records.forEach(accumulated::add);
            assertThat(accumulated).isNotEmpty();
            assertThat(accumulated.getFirst().key()).isEqualTo(nonExistentBookingId.toString());
        });

        dltConsumer.close();
    }
}
