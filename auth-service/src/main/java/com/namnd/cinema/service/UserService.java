package com.namnd.cinema.service;

import com.namnd.cinema.model.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;

public interface UserService extends UserDetailsService {

    void save(User user);

    Optional<User> findByEmail(String email);

    Boolean existsByEmail(String email);
}
