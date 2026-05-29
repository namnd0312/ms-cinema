package com.namnd.cinema.util;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;

import static org.junit.jupiter.api.Assertions.*;

class RsaKeyCryptoUtilTest {

    private static final char[] PASSWORD = "test-kek-password-min-32-characters-long".toCharArray();

    @Test
    void generateRsa2048_producesPair() {
        KeyPair kp = RsaKeyCryptoUtil.generateRsa2048();
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("RSA", kp.getPublic().getAlgorithm());
    }

    @Test
    void publicPemRoundtrip_preservesEncoding() {
        PublicKey original = RsaKeyCryptoUtil.generateRsa2048().getPublic();
        String pem = RsaKeyCryptoUtil.toPemPublic(original);
        PublicKey restored = RsaKeyCryptoUtil.fromPemPublic(pem);
        assertArrayEquals(original.getEncoded(), restored.getEncoded());
    }

    @Test
    void encryptDecryptPrivate_roundtripsCorrectly() {
        PrivateKey original = RsaKeyCryptoUtil.generateRsa2048().getPrivate();
        String ciphertext = RsaKeyCryptoUtil.encryptPrivatePem(original, PASSWORD);
        PrivateKey restored = RsaKeyCryptoUtil.decryptPrivatePem(ciphertext, PASSWORD);
        assertArrayEquals(original.getEncoded(), restored.getEncoded());
        assertInstanceOf(RSAPrivateKey.class, restored);
    }

    @Test
    void decryptWithWrongPassword_throws() {
        PrivateKey original = RsaKeyCryptoUtil.generateRsa2048().getPrivate();
        String ciphertext = RsaKeyCryptoUtil.encryptPrivatePem(original, PASSWORD);
        assertThrows(IllegalStateException.class, () ->
                RsaKeyCryptoUtil.decryptPrivatePem(ciphertext, "wrong-password-1234567890123456".toCharArray()));
    }

    @Test
    void encryptionProducesNonDeterministicCiphertext() {
        PrivateKey key = RsaKeyCryptoUtil.generateRsa2048().getPrivate();
        String c1 = RsaKeyCryptoUtil.encryptPrivatePem(key, PASSWORD);
        String c2 = RsaKeyCryptoUtil.encryptPrivatePem(key, PASSWORD);
        // Different random salt + IV per call -> ciphertexts must differ.
        assertNotEquals(c1, c2);
    }
}
