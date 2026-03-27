package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

public interface ActivationService {

    void createActivationToken(User user);

    void activateAccount(String token);

    void activateWithPassword(String token, String password);

    void resendActivationToken(String email);
}
