package com.namnd.cinema.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA keypair generation + AES-GCM envelope encryption for at-rest private keys.
 * Encryption scheme:
 *   salt(16) || iv(12) || ciphertext || gcmTag(16)  -- base64 encoded
 * KEK derived from caller-supplied password via PBKDF2-HMAC-SHA256 (100k iter).
 */
public final class RsaKeyCryptoUtil {

    private static final int RSA_KEY_SIZE = 2048;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_KEY_LEN_BITS = 256;
    private static final int SALT_LEN = 16;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN_BITS = 128;

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";

    private RsaKeyCryptoUtil() {}

    public static KeyPair generateRsa2048() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(RSA_KEY_SIZE, SecureRandom.getInstanceStrong());
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA keygen not available", e);
        }
    }

    /** PEM-encode (PKCS#1-ish — actually X.509 SubjectPublicKeyInfo wrapped in PEM headers). */
    public static String toPemPublic(PublicKey publicKey) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static PublicKey fromPemPublic(String pem) {
        try {
            byte[] der = pemToDer(pem, "PUBLIC KEY");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid PEM public key", e);
        }
    }

    /**
     * Encrypt the PKCS#8 PEM of a private key with AES-GCM under a PBKDF2-derived KEK.
     * Returned string is base64(salt || iv || ciphertext+tag).
     */
    public static String encryptPrivatePem(PrivateKey privateKey, char[] password) {
        try {
            String pem = toPemPrivate(privateKey);
            byte[] plaintext = pem.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            byte[] salt = randomBytes(SALT_LEN);
            byte[] iv = randomBytes(GCM_IV_LEN);
            SecretKey kek = deriveKek(password, salt);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);

            ByteBuffer buf = ByteBuffer.allocate(salt.length + iv.length + ct.length);
            buf.put(salt).put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Private key encryption failed", e);
        }
    }

    public static PrivateKey decryptPrivatePem(String encryptedBase64, char[] password) {
        try {
            byte[] all = Base64.getDecoder().decode(encryptedBase64);
            if (all.length < SALT_LEN + GCM_IV_LEN + GCM_TAG_LEN_BITS / 8) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            ByteBuffer buf = ByteBuffer.wrap(all);
            byte[] salt = new byte[SALT_LEN]; buf.get(salt);
            byte[] iv = new byte[GCM_IV_LEN]; buf.get(iv);
            byte[] ct = new byte[buf.remaining()]; buf.get(ct);

            SecretKey kek = deriveKek(password, salt);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            byte[] pemBytes = cipher.doFinal(ct);
            String pem = new String(pemBytes, java.nio.charset.StandardCharsets.UTF_8);
            byte[] der = pemToDer(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Private key decryption failed (wrong password or tampered ciphertext)", e);
        }
    }

    private static String toPemPrivate(PrivateKey privateKey) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }

    private static byte[] pemToDer(String pem, String type) {
        String stripped = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }

    private static SecretKey deriveKek(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LEN_BITS);
        try {
            byte[] keyBytes = SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        new SecureRandom().nextBytes(b);
        return b;
    }
}
