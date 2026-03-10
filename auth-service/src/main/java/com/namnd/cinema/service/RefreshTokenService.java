package com.namnd.cinema.service;

import com.namnd.cinema.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(Long userId);

    RefreshToken verifyExpiration(RefreshToken token);

    Optional<RefreshToken> findByToken(String token);

    void deleteByUserId(Long userId);

    /**
     * Atomically verify old token, create new refresh token (rotation).
     * Returns new RefreshToken or throws if expired/invalid.
     */
    RefreshToken rotateRefreshToken(RefreshToken oldToken);
}
