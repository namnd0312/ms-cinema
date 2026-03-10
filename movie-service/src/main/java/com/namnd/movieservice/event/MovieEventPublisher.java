package com.namnd.movieservice.event;

import com.namnd.kafka.events.domain.MovieCreatedEvent;
import com.namnd.kafka.events.domain.ShowtimeCreatedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publishes movie and showtime domain events to Kafka.
 * Fire-and-forget: Kafka failures logged but don't break movie creation.
 */
@Component
public class MovieEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(MovieEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MovieEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishMovieCreated(Long movieId, String title) {
        var event = new MovieCreatedEvent(movieId, title);
        var envelope = EventEnvelope.of("movie-service", "movie.created", movieId.toString(), event);
        try {
            kafkaTemplate.send(KafkaTopics.MOVIE_EVENTS, movieId.toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish movie.created for movieId={}: {}", movieId, ex.getMessage());
                    else log.info("Published movie.created: movieId={}", movieId);
                });
        } catch (Exception e) {
            log.error("Error publishing movie.created for movieId={}: {}", movieId, e.getMessage(), e);
        }
    }

    public void publishShowtimeCreated(Long showtimeId, Long movieId, Long theaterId, LocalDateTime startTime, Integer availableSeats) {
        var event = new ShowtimeCreatedEvent(showtimeId, movieId, theaterId, startTime, availableSeats);
        var envelope = EventEnvelope.of("movie-service", "showtime.created", showtimeId.toString(), event);
        try {
            kafkaTemplate.send(KafkaTopics.MOVIE_EVENTS, showtimeId.toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish showtime.created for showtimeId={}: {}", showtimeId, ex.getMessage());
                    else log.info("Published showtime.created: showtimeId={}", showtimeId);
                });
        } catch (Exception e) {
            log.error("Error publishing showtime.created for showtimeId={}: {}", showtimeId, e.getMessage(), e);
        }
    }
}
