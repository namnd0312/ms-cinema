package com.namnd.bookingservice.model;

/**
 * Lifecycle states for a booking.
 * PENDING    → seats locked, awaiting payment
 * CONFIRMED  → payment successful, booking finalised
 * CANCELLED  → user or system cancelled before confirmation
 * EXPIRED    → payment window elapsed without confirmation
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
