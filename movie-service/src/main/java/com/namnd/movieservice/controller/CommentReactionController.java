package com.namnd.movieservice.controller;

import com.namnd.movieservice.dto.CommentReactionDto;
import com.namnd.movieservice.dto.CommentReactionRequest;
import com.namnd.movieservice.service.CommentReactionService;
import com.namnd.jwt.autoconfigure.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for comment like/dislike reactions. All endpoints require auth.
 */
@RestController
@RequestMapping("/api/comments/{commentId}/reactions")
@RequiredArgsConstructor
@Tag(name = "Comment Reactions", description = "Like/dislike endpoints")
public class CommentReactionController {

    private final CommentReactionService reactionService;

    @Operation(summary = "Toggle like/dislike on a comment")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentReactionDto> toggle(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentReactionRequest request) {
        return ResponseEntity.ok(reactionService.toggleReaction(commentId, extractUserId(), request));
    }

    @Operation(summary = "Remove reaction from a comment")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentReactionDto> remove(@PathVariable Long commentId) {
        return ResponseEntity.ok(reactionService.removeReaction(commentId, extractUserId()));
    }

    private Long extractUserId() {
        JwtAuthenticatedUser user = (JwtAuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.userId();
    }
}
