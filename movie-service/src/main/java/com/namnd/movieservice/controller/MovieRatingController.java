package com.namnd.movieservice.controller;

import com.namnd.movieservice.dto.CreateRatingRequest;
import com.namnd.movieservice.dto.MovieRatingDto;
import com.namnd.movieservice.dto.MovieRatingSummaryDto;
import com.namnd.movieservice.service.MovieRatingService;
import com.namnd.jwt.autoconfigure.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for movie star ratings (1-5). GET is public; POST requires auth.
 */
@RestController
@RequestMapping("/api/movies/{movieId}/ratings")
@RequiredArgsConstructor
@Tag(name = "Movie Ratings", description = "Star rating endpoints")
public class MovieRatingController {

    private final MovieRatingService ratingService;

    @Operation(summary = "Rate a movie (1-5 stars, creates or updates)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovieRatingDto> rate(
            @PathVariable Long movieId,
            @Valid @RequestBody CreateRatingRequest request) {
        return ResponseEntity.ok(ratingService.createOrUpdateRating(movieId, extractUserId(), request));
    }

    @Operation(summary = "Get rating summary for a movie")
    @GetMapping
    public ResponseEntity<MovieRatingSummaryDto> getSummary(@PathVariable Long movieId) {
        return ResponseEntity.ok(ratingService.getRatingSummary(movieId, extractUserIdOrNull()));
    }

    private Long extractUserId() {
        JwtAuthenticatedUser user = (JwtAuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.userId();
    }

    private Long extractUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser user) {
            return user.userId();
        }
        return null;
    }
}
