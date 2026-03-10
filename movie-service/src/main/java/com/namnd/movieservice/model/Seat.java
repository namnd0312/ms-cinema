package com.namnd.movieservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Individual seat within a theater. Row label (A-Z) + seat number uniquely identify a seat.
 */
@Entity
@Table(
    name = "seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_seat_theater_row_number",
        columnNames = {"theater_id", "row_label", "seat_number"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    private String rowLabel;

    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType = SeatType.STANDARD;

    @Column(precision = 5, scale = 2)
    private BigDecimal priceMultiplier = BigDecimal.ONE;
}
