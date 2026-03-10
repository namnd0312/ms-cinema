package com.namnd.cinema.controller;

import com.namnd.cinema.dto.UserInfoResponseDto;
import com.namnd.cinema.dto.ValidateTokenRequestDto;
import com.namnd.cinema.dto.ValidateTokenResponseDto;
import com.namnd.cinema.model.Role;
import com.namnd.cinema.model.User;
import com.namnd.cinema.model.UserPrinciple;
import com.namnd.cinema.service.BlacklistedTokenService;
import com.namnd.cinema.service.JwtService;
import com.namnd.cinema.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Provides endpoints for microservice-to-microservice JWT validation and
 * authenticated user profile retrieval.
 *
 * POST /api/auth/validate-token — stateless token check (no DB hit, Redis blacklist only)
 * GET  /api/users/me            — returns fresh user profile for authenticated caller
 */
@RestController
@Tag(name = "Token Validation", description = "JWT validation and user profile endpoints")
public class TokenValidationController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private BlacklistedTokenService blacklistedTokenService;

    @Autowired
    private UserService userService;

    /**
     * Validates a JWT token without hitting the database.
     * Checks signature/expiry via JJWT and blacklist status via Redis.
     * Returns valid=false for any invalid/expired/blacklisted token.
     */
    @Operation(summary = "Validate a JWT token (blacklist + signature check)")
    @PostMapping("/api/auth/validate-token")
    public ResponseEntity<ValidateTokenResponseDto> validateToken(
            @Valid @RequestBody ValidateTokenRequestDto request) {
        try {
            String token = request.getToken();

            if (!jwtService.validateJwtToken(token)) {
                return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                        .valid(false).build());
            }

            // Check Redis blacklist by JTI
            String jti = jwtService.getJtiFromToken(token);
            if (jti != null && blacklistedTokenService.isTokenBlacklisted(jti)) {
                return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                        .valid(false).build());
            }

            return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                    .valid(true)
                    .userId(jwtService.getUserIdFromToken(token))
                    .email(jwtService.getEmailFromJwtToken(token))
                    .roles(jwtService.getRolesFromToken(token))
                    .build());

        } catch (Exception e) {
            return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                    .valid(false).build());
        }
    }

    /**
     * Returns the authenticated user's profile fetched fresh from the database.
     * Requires a valid Bearer token — Spring Security enforces authentication
     * because this path is outside the /api/auth/** permitAll pattern.
     */
    @Operation(summary = "Get authenticated user profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/users/me")
    public ResponseEntity<UserInfoResponseDto> getUserInfo(
            @AuthenticationPrincipal UserPrinciple userPrinciple) {

        User user = userService.findByEmail(userPrinciple.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        return ResponseEntity.ok(UserInfoResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(roleNames)
                .build());
    }
}
