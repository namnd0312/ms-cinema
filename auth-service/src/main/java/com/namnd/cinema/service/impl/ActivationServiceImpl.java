package com.namnd.cinema.service.impl;

import com.namnd.cinema.model.ActivationToken;
import com.namnd.cinema.model.User;
import com.namnd.cinema.repository.ActivationTokenRepository;
import com.namnd.cinema.repository.UserRepository;
import com.namnd.cinema.service.ActivationService;
import com.namnd.cinema.service.EmailService;
import com.namnd.cinema.service.PasswordHistoryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class ActivationServiceImpl implements ActivationService {

    private static final Logger logger = LoggerFactory.getLogger(ActivationServiceImpl.class);
    // 24 hours in milliseconds
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Override
    @Transactional
    public void createActivationToken(User user) {
        // Delete any previous unused activation tokens for this user
        activationTokenRepository.deleteByUserAndUsedFalse(user);

        String tokenValue = UUID.randomUUID().toString();

        ActivationToken activationToken = new ActivationToken();
        activationToken.setToken(tokenValue);
        activationToken.setUser(user);
        activationToken.setExpiryDate(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS));
        activationToken.setUsed(false);

        activationTokenRepository.save(activationToken);
        emailService.sendActivationEmail(user.getEmail(), tokenValue);
        logger.info("Activation token created for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void activateAccount(String token) {
        // Generic error message to prevent token state enumeration
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation token."));

        if (activationToken.isUsed() || activationToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Invalid or expired activation token.");
        }

        User user = activationToken.getUser();
        user.setActive(true);
        userRepository.save(user);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
        logger.info("Account activated for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void activateWithPassword(String token, String password) {
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation token."));

        if (activationToken.isUsed() || activationToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Invalid or expired activation token.");
        }

        User user = activationToken.getUser();
        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);
        user.setActive(true);
        userRepository.save(user);

        passwordHistoryService.savePasswordToHistory(user, encodedPassword);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
        logger.info("Account activated with password for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendActivationToken(String email) {
        // Silent no-op if user not found or already active (prevent email enumeration)
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (!userOptional.isPresent() || userOptional.get().isActive()) {
            return;
        }

        createActivationToken(userOptional.get());
    }
}
