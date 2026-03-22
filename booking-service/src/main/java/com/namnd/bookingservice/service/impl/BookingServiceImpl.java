package com.namnd.bookingservice.service.impl;

import com.namnd.bookingservice.client.MovieServiceClient;
import com.namnd.bookingservice.dto.*;
import com.namnd.bookingservice.exception.BookingNotFoundException;
import com.namnd.bookingservice.exception.SeatAlreadyLockedException;
import com.namnd.bookingservice.model.Booking;
import com.namnd.bookingservice.model.BookingSeat;
import com.namnd.bookingservice.model.BookingStatus;
import com.namnd.bookingservice.repository.BookingRepository;
import com.namnd.bookingservice.service.BookingService;
import com.namnd.bookingservice.service.SeatLockService;
import com.namnd.bookingservice.websocket.SeatWebSocketPublisher;
import com.namnd.kafka.events.audit.Auditable;
import com.namnd.kafka.events.domain.AuditAction;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core booking business logic: seat reservation, confirmation, and cancellation.
 */
@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final SeatLockService seatLockService;
    private final MovieServiceClient movieServiceClient;
    private final SeatWebSocketPublisher seatWebSocketPublisher;
    private final Counter bookingCreatedCounter;
    private final Counter bookingConfirmedCounter;
    private final Counter bookingCancelledCounter;

    public BookingServiceImpl(BookingRepository bookingRepository,
                               SeatLockService seatLockService,
                               MovieServiceClient movieServiceClient,
                               SeatWebSocketPublisher seatWebSocketPublisher,
                               Counter bookingCreatedCounter,
                               Counter bookingConfirmedCounter,
                               Counter bookingCancelledCounter) {
        this.bookingRepository = bookingRepository;
        this.seatLockService = seatLockService;
        this.movieServiceClient = movieServiceClient;
        this.seatWebSocketPublisher = seatWebSocketPublisher;
        this.bookingCreatedCounter = bookingCreatedCounter;
        this.bookingConfirmedCounter = bookingConfirmedCounter;
        this.bookingCancelledCounter = bookingCancelledCounter;
    }

    @Override
    @Auditable(action = AuditAction.CREATE, entityType = "Booking")
    public BookingResponseDto reserve(Long userId, BookingRequestDto request) {
        ShowtimeInfoDto showtime = movieServiceClient.getShowtime(request.showtimeId());
        List<SeatInfoDto> allSeats = movieServiceClient.getSeatsForShowtime(request.showtimeId());

        // Index requested seats by ID for O(1) lookup
        Map<Long, SeatInfoDto> requestedSeats = allSeats.stream()
                .filter(s -> request.seatIds().contains(s.id()))
                .collect(Collectors.toMap(SeatInfoDto::id, s -> s));

        boolean locked = seatLockService.lockSeats(request.showtimeId(), request.seatIds(), userId);
        if (!locked) {
            throw new SeatAlreadyLockedException(request.showtimeId());
        }

        BigDecimal basePrice = showtime.basePrice();
        long totalAmount = 0L;

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowtimeId(request.showtimeId());
        booking.setStatus(BookingStatus.PENDING);
        booking.setReservedAt(LocalDateTime.now());
        booking.setExpiresAt(LocalDateTime.now().plusSeconds(300));

        for (Long seatId : request.seatIds()) {
            SeatInfoDto seat = requestedSeats.get(seatId);
            long seatPrice = basePrice.multiply(seat.priceMultiplier())
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            totalAmount += seatPrice;

            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setShowtimeId(request.showtimeId());
            bookingSeat.setSeatId(seatId);
            bookingSeat.setSeatLabel(seat.rowLabel() + seat.seatNumber());
            bookingSeat.setSeatType(seat.seatType());
            bookingSeat.setPrice(seatPrice);
            booking.getSeats().add(bookingSeat);
        }

        booking.setTotalAmount(totalAmount);
        Booking saved = bookingRepository.save(booking);
        bookingCreatedCounter.increment();
        try {
            seatWebSocketPublisher.publishSeatUpdate(request.showtimeId(), request.seatIds(), "RESERVED");
        } catch (Exception e) {
            // Non-critical: WS notification failure should not break the booking
        }
        return toResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDto getBooking(Long bookingId, Long userId) {
        Booking booking = findById(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new BookingNotFoundException(bookingId);
        }
        return toResponseDto(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Override
    public void confirmBooking(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) return;
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        bookingConfirmedCounter.increment();
        // Seats are now permanently booked; release Redis locks
        List<Long> seatIds = booking.getSeats().stream().map(BookingSeat::getSeatId).toList();
        seatLockService.unlockSeats(booking.getShowtimeId(), seatIds);
        try {
            seatWebSocketPublisher.publishSeatUpdate(booking.getShowtimeId(), seatIds, "CONFIRMED");
        } catch (Exception e) {
            // Non-critical: WS notification failure should not break confirmation
        }
    }

    @Override
    @Auditable(action = AuditAction.UPDATE, entityType = "Booking")
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = findById(bookingId);
        // userId null = system-initiated cancel, skip ownership check
        if (userId != null && !booking.getUserId().equals(userId)) {
            throw new BookingNotFoundException(bookingId);
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingCancelledCounter.increment();
        List<Long> seatIds = booking.getSeats().stream().map(BookingSeat::getSeatId).toList();
        seatLockService.unlockSeats(booking.getShowtimeId(), seatIds);
        try {
            seatWebSocketPublisher.publishSeatUpdate(booking.getShowtimeId(), seatIds, "RELEASED");
        } catch (Exception e) {
            // Non-critical: WS notification failure should not break cancellation
        }
    }

    // --- helpers ---

    private Booking findById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private BookingResponseDto toResponseDto(Booking booking) {
        List<BookingSeatDto> seats = booking.getSeats().stream()
                .map(s -> new BookingSeatDto(s.getSeatId(), s.getSeatLabel(),
                        s.getSeatType(), s.getPrice()))
                .toList();
        return new BookingResponseDto(
                booking.getId(),
                booking.getShowtimeId(),
                booking.getStatus().name(),
                booking.getTotalAmount(),
                seats,
                booking.getReservedAt(),
                booking.getExpiresAt()
        );
    }
}
