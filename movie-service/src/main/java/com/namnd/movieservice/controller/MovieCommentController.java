package com.namnd.movieservice.controller;

import com.namnd.movieservice.dto.CreateCommentRequest;
import com.namnd.movieservice.dto.MovieCommentDto;
import com.namnd.movieservice.dto.UpdateCommentRequest;
import com.namnd.movieservice.service.MovieCommentService;
import com.namnd.jwt.autoconfigure.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for movie comments. GET is public; mutations require auth.
 * PUT/DELETE use /api/comments/{id} path (not nested under movies).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Movie Comments", description = "Comment endpoints")
public class MovieCommentController {

    private final MovieCommentService commentService;

    @Operation(summary = "Post a comment on a movie")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/movies/{movieId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovieCommentDto> create(
            @PathVariable Long movieId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(movieId, extractUserId(), request));
    }

    @Operation(summary = "List comments for a movie (paginated)")
    @GetMapping("/api/movies/{movieId}/comments")
    public ResponseEntity<Page<MovieCommentDto>> list(
            @PathVariable Long movieId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(commentService.getCommentsByMovie(movieId, extractUserIdOrNull(), pageable));
    }

    @Operation(summary = "Update own comment")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovieCommentDto> update(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(commentService.updateComment(commentId, extractUserId(), request));
    }

    @Operation(summary = "Delete own comment (admin can delete any)")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/comments/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long commentId) {
        JwtAuthenticatedUser user = (JwtAuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAdmin = user.roles().contains("ROLE_ADMIN");
        commentService.deleteComment(commentId, user.userId(), isAdmin);
        return ResponseEntity.noContent().build();
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
