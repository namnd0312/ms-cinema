package com.namnd.cinema.repository;

import com.namnd.cinema.model.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findByToken(String token);
    void deleteByUserAndUsedFalse(com.namnd.cinema.model.User user);
}
