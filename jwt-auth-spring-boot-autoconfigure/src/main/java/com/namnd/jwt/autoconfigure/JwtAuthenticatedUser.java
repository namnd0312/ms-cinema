package com.namnd.jwt.autoconfigure;

import java.util.List;

/**
 * Lightweight principal object populated from JWT claims.
 * Set as principal on UsernamePasswordAuthenticationToken in SecurityContext.
 * No dependency on auth-service's UserPrinciple or any JPA entity.
 */
public record JwtAuthenticatedUser(
        Long userId,
        String email,
        List<String> roles
) {}
