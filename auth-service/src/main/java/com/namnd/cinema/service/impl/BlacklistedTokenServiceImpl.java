package com.namnd.cinema.service.impl;

import com.namnd.cinema.config.RedisKeyPrefix;
import com.namnd.cinema.service.BlacklistedTokenService;
import com.namnd.cinema.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class BlacklistedTokenServiceImpl implements BlacklistedTokenService {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistedTokenServiceImpl.class);

    private final RedisService redisService;

    public BlacklistedTokenServiceImpl(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void blacklistToken(String jti, Date expiryDate) {
        long ttlSeconds = (expiryDate.getTime() - System.currentTimeMillis()) / 1000;

        if (ttlSeconds <= 0) {
            logger.debug("Token already expired, skipping blacklist (JTI: {})", jti);
            return;
        }

        String key = RedisKeyPrefix.BLACKLIST + jti;
        redisService.set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        logger.debug("Token blacklisted (JTI: {}), TTL: {}s", jti, ttlSeconds);
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        try {
            String key = RedisKeyPrefix.BLACKLIST + jti;
            return Boolean.TRUE.equals(redisService.hasKey(key));
        } catch (Exception e) {
            // Fail-closed: deny token if Redis is unavailable (security requirement)
            logger.error("Redis unavailable during blacklist check (JTI: {}). Denying request.", jti);
            return true;
        }
    }
}
