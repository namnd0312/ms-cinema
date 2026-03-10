package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

public interface AccountLockService {
    void registerFailedAttempt(String email);
    void resetFailedAttempts(String email);
    boolean unlockIfExpired(User user);
    boolean isLocked(User user);
    long getRemainingLockTimeMs(User user);
}
