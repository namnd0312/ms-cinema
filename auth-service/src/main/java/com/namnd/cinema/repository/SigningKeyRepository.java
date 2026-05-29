package com.namnd.cinema.repository;

import com.namnd.cinema.model.KeyStatus;
import com.namnd.cinema.model.SigningKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKey, Long> {

    /** Locate the (single) ACTIVE signing key. Empty on first boot before bootstrap runs. */
    Optional<SigningKey> findFirstByStatus(KeyStatus status);

    /** All keys to publish in JWKS — ACTIVE + RETIRED — so existing tokens still verify. */
    List<SigningKey> findByStatusIn(List<KeyStatus> statuses);

    Optional<SigningKey> findByKid(String kid);

    void deleteByKid(String kid);
}
