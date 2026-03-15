package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

public interface PasswordHistoryService {
    boolean isPasswordReused(User user, String rawNewPassword);
    void savePasswordToHistory(User user, String encodedPassword);
}
