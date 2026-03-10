package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

public interface ActivationService {

    void createActivationToken(User user);

    void activateAccount(String token);

    void resendActivationToken(String email);
}
