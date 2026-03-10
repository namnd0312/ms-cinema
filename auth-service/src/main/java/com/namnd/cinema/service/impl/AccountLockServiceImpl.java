package com.namnd.cinema.service.impl;

import com.namnd.cinema.model.User;
import com.namnd.cinema.repository.UserRepository;
import com.namnd.cinema.service.AccountLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.Optional;

@Service
public class AccountLockServiceImpl implements AccountLockService {

    private static final Logger logger = LoggerFactory.getLogger(AccountLockServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Value("${namnd.app.maxFailedAttempts}")
    private int maxFailedAttempts;

    @Value("${namnd.app.lockDurationMs}")
    private long lockDurationMs;

    @Override
    @Transactional
    public void registerFailedAttempt(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent()) return;

        User user = userOpt.get();
        int newFailedAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(newFailedAttempts);

        if (newFailedAttempts >= maxFailedAttempts) {
            user.setLockTime(new Date());
            logger.warn("Account locked for email: {} after {} failed attempts", email, newFailedAttempts);
        }

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resetFailedAttempts(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent()) return;

        User user = userOpt.get();
        user.setFailedAttempts(0);
        user.setLockTime(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public boolean unlockIfExpired(User user) {
        if (user.getLockTime() == null) return true;

        long lockTimeMs = user.getLockTime().getTime();
        long now = System.currentTimeMillis();

        if (now - lockTimeMs >= lockDurationMs) {
            user.setFailedAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
            logger.info("Account auto-unlocked for email: {}", user.getEmail());
            return true;
        }

        return false;
    }

    @Override
    public boolean isLocked(User user) {
        if (user.getLockTime() == null) return false;
        long elapsed = System.currentTimeMillis() - user.getLockTime().getTime();
        return elapsed < lockDurationMs;
    }

    @Override
    public long getRemainingLockTimeMs(User user) {
        if (user.getLockTime() == null) return 0;
        long elapsed = System.currentTimeMillis() - user.getLockTime().getTime();
        long remaining = lockDurationMs - elapsed;
        return Math.max(remaining, 0);
    }
}
