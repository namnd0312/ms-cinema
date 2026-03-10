package com.namnd.cinema.config;

/**
 * Centralized Redis key prefix constants.
 * All Redis keys must use a prefix from this class to avoid collisions
 * and keep key naming consistent across the project.
 */
public final class RedisKeyPrefix {

    private RedisKeyPrefix() {} // prevent instantiation

    public static final String BLACKLIST = "blacklist:";
    public static final String LOCK = "lock:";
    // Add future prefixes here (e.g., CACHE, SESSION, RATE_LIMIT)
}
