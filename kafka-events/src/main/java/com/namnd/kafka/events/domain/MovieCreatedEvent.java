package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event published when a new movie is added to the catalog.
 * Consumed by services that maintain local movie references.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieCreatedEvent(
        Long movieId,
        String title
) {}
