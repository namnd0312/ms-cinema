package com.namnd.cinema.service.impl;

import com.namnd.cinema.model.PasswordHistory;
import com.namnd.cinema.model.User;
import com.namnd.cinema.repository.PasswordHistoryRepository;
import com.namnd.cinema.service.PasswordHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PasswordHistoryServiceImpl implements PasswordHistoryService {

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public boolean isPasswordReused(User user, String rawNewPassword) {
        List<PasswordHistory> recentPasswords =
            passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user);
        return recentPasswords.stream()
            .anyMatch(ph -> passwordEncoder.matches(rawNewPassword, ph.getPasswordHash()));
    }

    @Override
    @Transactional
    public void savePasswordToHistory(User user, String encodedPassword) {
        PasswordHistory history = new PasswordHistory();
        history.setUser(user);
        history.setPasswordHash(encodedPassword);
        passwordHistoryRepository.save(history);
        passwordHistoryRepository.deleteOldEntriesByUser(user);
    }
}
