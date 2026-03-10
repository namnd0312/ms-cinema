package com.namnd.bookingservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of a single seat within a booking.
 * Price and seat metadata are captured at reservation time.
 */
@Entity
@Table(name = "booking_seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private Long showtimeId;

    @Column(nullable = false)
    private Long seatId;

    /** Human-readable seat label, e.g. "A5". */
    @Column(nullable = false)
    private String seatLabel;

    /** Seat category: STANDARD, VIP, or PREMIUM. */
    @Column(nullable = false)
    private String seatType;

    /** Price in VND at time of reservation (snapshot). */
    @Column(nullable = false)
    private Long price;
}
