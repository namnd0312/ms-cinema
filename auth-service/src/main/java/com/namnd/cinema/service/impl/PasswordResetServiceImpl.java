package com.namnd.cinema.service.impl;

import com.namnd.cinema.model.PasswordResetToken;
import com.namnd.cinema.model.User;
import com.namnd.cinema.repository.PasswordResetTokenRepository;
import com.namnd.cinema.repository.UserRepository;
import com.namnd.cinema.service.EmailService;
import com.namnd.cinema.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final long TOKEN_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void createPasswordResetToken(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (!userOptional.isPresent()) {
            // Don't reveal whether email exists
            logger.debug("Password reset requested for non-existent email");
            return;
        }

        User user = userOptional.get();
        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(tokenValue);
        resetToken.setUser(user);
        resetToken.setExpiryDate(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS));
        resetToken.setUsed(false);

        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(email, tokenValue);
        logger.info("Password reset token created for user: {}", user.getUsername());
    }

    @Override
    public boolean validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByToken(token);
        if (!tokenOptional.isPresent()) {
            return false;
        }

        PasswordResetToken resetToken = tokenOptional.get();
        return !resetToken.isUsed() && resetToken.getExpiryDate().after(new Date());
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByToken(token);
        if (!tokenOptional.isPresent()) {
            throw new RuntimeException("Invalid password reset token");
        }

        PasswordResetToken resetToken = tokenOptional.get();
        if (resetToken.isUsed()) {
            throw new RuntimeException("Password reset token already used");
        }
        if (resetToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Password reset token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        logger.info("Password reset successful for user: {}", user.getUsername());
    }
}
