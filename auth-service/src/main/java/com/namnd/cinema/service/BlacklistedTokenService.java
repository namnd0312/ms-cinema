package com.namnd.cinema.service;

import java.util.Date;

public interface BlacklistedTokenService {

    void blacklistToken(String jti, Date expiryDate);

    boolean isTokenBlacklisted(String jti);
}
