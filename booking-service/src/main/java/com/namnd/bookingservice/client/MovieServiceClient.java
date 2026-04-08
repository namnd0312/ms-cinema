package com.namnd.bookingservice.client;

import com.namnd.bookingservice.dto.SeatInfoDto;
import com.namnd.bookingservice.dto.ShowtimeInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client for communicating with movie-service.
 * Retrieves showtime details and seat information for booking validation.
 */
@FeignClient(name = "movie-service", url = "${movie.service.url:}")
public interface MovieServiceClient {

    @GetMapping("/api/showtimes/{id}")
    ShowtimeInfoDto getShowtime(@PathVariable("id") Long id);

    @GetMapping("/api/showtimes/{id}/seats")
    List<SeatInfoDto> getSeatsForShowtime(@PathVariable("id") Long id);
}
