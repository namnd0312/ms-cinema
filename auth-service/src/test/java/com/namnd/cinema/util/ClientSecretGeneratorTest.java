package com.namnd.cinema.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientSecretGeneratorTest {

    private final ClientSecretGenerator generator = new ClientSecretGenerator();

    @Test
    void generate_producesBase64UrlString() {
        String secret = generator.generate();
        assertNotNull(secret);
        // 48 bytes -> 64 chars base64url (unpadded)
        assertEquals(64, secret.length());
        assertTrue(secret.matches("[A-Za-z0-9_-]+"), "Must be base64url alphabet");
    }

    @Test
    void generate_isHighEntropy_noCollisionsAcross10kCalls() {
        Set<String> all = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(all.add(generator.generate()), "Duplicate secret at iteration " + i);
        }
    }
}
