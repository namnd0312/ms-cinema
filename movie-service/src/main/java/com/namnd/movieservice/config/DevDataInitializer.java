package com.namnd.movieservice.config;

import com.namnd.movieservice.model.*;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.repository.SeatRepository;
import com.namnd.movieservice.repository.ShowtimeRepository;
import com.namnd.movieservice.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds sample movies, theaters, seats, and showtimes for development.
 * Only inserts if the database is empty (idempotent).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final MovieRepository movieRepository;
    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;

    @Override
    public void run(String... args) {
        if (movieRepository.count() > 0) {
            log.info("Database already seeded — skipping");
            return;
        }
        seedMovies();
        List<Theater> theaters = seedTheaters();
        seedShowtimes(theaters);
        log.info("Dev data seeded successfully");
    }

    private void seedMovies() {
        List<Movie> movies = List.of(
            movie("Inception", "A thief who steals corporate secrets through dream-sharing technology",
                "Sci-Fi", 148, "PG-13", LocalDate.of(2010, 7, 16)),
            movie("The Dark Knight", "Batman raises the stakes in his war on crime",
                "Action", 152, "PG-13", LocalDate.of(2008, 7, 18)),
            movie("Interstellar", "A team of explorers travel through a wormhole in space",
                "Sci-Fi", 169, "PG-13", LocalDate.of(2014, 11, 7)),
            movie("Parasite", "Greed and class discrimination threaten a symbiotic relationship",
                "Thriller", 132, "R", LocalDate.of(2019, 5, 30)),
            movie("Dune: Part Two", "Paul Atreides unites with the Fremen to avenge his family",
                "Sci-Fi", 166, "PG-13", LocalDate.of(2024, 3, 1))
        );
        movieRepository.saveAll(movies);
        log.info("Seeded {} movies", movies.size());
    }

    private List<Theater> seedTheaters() {
        Theater imax = theater("IMAX Hall 1", "District 1, Ho Chi Minh City", 10, 15);
        Theater standard = theater("Standard Screen 2", "District 7, Ho Chi Minh City", 8, 12);
        Theater vip = theater("VIP Lounge 3", "District 2, Ho Chi Minh City", 5, 8);
        List<Theater> theaters = theaterRepository.saveAll(List.of(imax, standard, vip));

        for (Theater t : theaters) {
            generateSeats(t);
        }
        log.info("Seeded {} theaters with seats", theaters.size());
        return theaters;
    }

    private void generateSeats(Theater theater) {
        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < theater.getTotalRows(); r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            // Last 2 rows are VIP with 1.5x multiplier
            boolean isVip = r >= theater.getTotalRows() - 2;
            for (int c = 1; c <= theater.getTotalColumns(); c++) {
                Seat seat = new Seat();
                seat.setTheater(theater);
                seat.setRowLabel(rowLabel);
                seat.setSeatNumber(c);
                seat.setSeatType(isVip ? SeatType.VIP : SeatType.STANDARD);
                seat.setPriceMultiplier(isVip ? new BigDecimal("1.5") : BigDecimal.ONE);
                seats.add(seat);
            }
        }
        seatRepository.saveAll(seats);
    }

    private void seedShowtimes(List<Theater> theaters) {
        List<Movie> movies = movieRepository.findAll();
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atTime(10, 0);
        List<Showtime> showtimes = new ArrayList<>();

        for (int i = 0; i < movies.size() && i < theaters.size(); i++) {
            Movie movie = movies.get(i);
            Theater theater = theaters.get(i % theaters.size());

            // Morning show
            showtimes.add(showtime(movie, theater, tomorrow.plusHours(i * 3),
                new BigDecimal("75000")));
            // Evening show
            showtimes.add(showtime(movie, theater, tomorrow.plusHours(i * 3 + 7),
                new BigDecimal("95000")));
        }
        // Extra showtimes for remaining movies in first theater
        for (int i = theaters.size(); i < movies.size(); i++) {
            showtimes.add(showtime(movies.get(i), theaters.get(0),
                tomorrow.plusDays(1).plusHours(i * 3), new BigDecimal("85000")));
        }

        showtimeRepository.saveAll(showtimes);
        log.info("Seeded {} showtimes", showtimes.size());
    }

    // --- Factory helpers ---

    private Movie movie(String title, String desc, String genre,
                        int duration, String rating, LocalDate release) {
        Movie m = new Movie();
        m.setTitle(title);
        m.setDescription(desc);
        m.setGenre(genre);
        m.setDurationMin(duration);
        m.setRating(rating);
        m.setReleaseDate(release);
        m.setStatus(MovieStatus.ACTIVE);
        return m;
    }

    private Theater theater(String name, String location, int rows, int cols) {
        Theater t = new Theater();
        t.setName(name);
        t.setLocation(location);
        t.setTotalRows(rows);
        t.setTotalColumns(cols);
        return t;
    }

    private Showtime showtime(Movie movie, Theater theater,
                              LocalDateTime start, BigDecimal price) {
        Showtime s = new Showtime();
        s.setMovie(movie);
        s.setTheater(theater);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(movie.getDurationMin()));
        s.setBasePrice(price);
        s.setStatus(ShowtimeStatus.SCHEDULED);
        return s;
    }
}
