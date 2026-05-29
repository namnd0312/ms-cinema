package com.namnd.cinema.config;

import com.namnd.cinema.service.SigningKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Ensures exactly one ACTIVE RSA signing key exists at boot.
 * Race-safe: partial unique index on signing_keys rejects concurrent inserts;
 * losing pod logs and re-reads the winner row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SigningKeyBootstrap implements ApplicationRunner {

    private final SigningKeyService signingKeyService;

    @Override
    public void run(ApplicationArguments args) {
        if (signingKeyService.findActive().isPresent()) {
            log.info("ACTIVE signing key already present; skipping bootstrap.");
            return;
        }
        try {
            signingKeyService.generateAndPersistActive();
        } catch (DataIntegrityViolationException race) {
            log.info("Concurrent bootstrap won by peer pod — partial-unique index rejected duplicate ACTIVE. Continuing.");
        }
    }
}
