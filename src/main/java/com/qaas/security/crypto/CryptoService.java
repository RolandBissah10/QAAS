package com.qaas.security.crypto;

import com.qaas.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authenticated encryption for secrets held at rest (project crawl credentials).
 *
 * <p>AES-256-GCM with a fresh random IV per value. The IV is not secret and is stored
 * alongside the ciphertext; GCM's auth tag means a tampered value fails to decrypt rather
 * than decrypting to garbage.
 *
 * <p>Stored form is {@code enc:v1:<base64(iv || ciphertext||tag)>}. The prefix lets
 * {@link EncryptedStringConverter} tell encrypted values from rows written before
 * encryption existed, so the change is backward compatible — see that class.
 */
@Service
public class CryptoService {

    /** Marks a value as produced by this class. Also carries a version for future re-keying. */
    static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;          // 96-bit IV, the GCM-recommended size
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(AppProperties properties) {
        String secret = properties.crypto() == null ? null : properties.crypto().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("qaas.crypto.secret must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("qaas.crypto.secret must contain at least 32 UTF-8 bytes");
        }
        // SHA-256 derives a uniform 256-bit key from a human-set secret of any length.
        this.key = new SecretKeySpec(sha256(secret), "AES");
    }

    /** Returns the encrypted, prefixed form of {@code plaintext}; null/blank pass through. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Never surface the plaintext in the message or cause.
            throw new IllegalStateException("Unable to encrypt value", e);
        }
    }

    /**
     * Reverses {@link #encrypt}. A value without the prefix is returned unchanged: it predates
     * encryption, and refusing it would make existing projects unreadable.
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (combined.length <= IV_BYTES) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt value", e);
        }
    }

    /** True if the value is in this class' encrypted form. */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
