package com.qaas.security.crypto;

import com.qaas.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for encryption of secrets at rest.
 *
 * <p>The point of this layer is that access control stops being the only thing protecting
 * crawl credentials: a leaked database dump must not yield usable passwords.
 */
class CryptoServiceTest {

    private static final String SECRET = "test-crypto-secret-at-least-32-characters-long";

    private CryptoService service(String secret) {
        return new CryptoService(new AppProperties(
                new AppProperties.Jwt("jwt-secret-at-least-32-characters-long!!", 30, 14),
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Mail(false, "noreply@qaas.local"),
                new AppProperties.Crypto(secret)));
    }

    private CryptoService service() {
        return service(SECRET);
    }

    @Test
    void roundTripsAValue() {
        CryptoService crypto = service();
        assertThat(crypto.decrypt(crypto.encrypt("hunter2"))).isEqualTo("hunter2");
    }

    @Test
    void ciphertextDoesNotContainThePlaintext() {
        String encrypted = service().encrypt("super-secret-password");

        assertThat(encrypted).doesNotContain("super-secret-password");
        assertThat(encrypted).startsWith("enc:v1:");
    }

    /** A fresh IV per call means identical inputs must not produce identical ciphertext. */
    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        CryptoService crypto = service();

        String first = crypto.encrypt("same-password");
        String second = crypto.encrypt("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo(crypto.decrypt(second)).isEqualTo("same-password");
    }

    @Test
    void roundTripsUnicodeAndLongValues() {
        CryptoService crypto = service();
        String tricky = "pässwörd-é中文-" + "x".repeat(500);

        assertThat(crypto.decrypt(crypto.encrypt(tricky))).isEqualTo(tricky);
    }

    @Test
    void nullAndEmptyPassThroughUnchanged() {
        CryptoService crypto = service();

        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.encrypt("")).isEmpty();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.decrypt("")).isEmpty();
    }

    /** Rows written before encryption existed have no prefix and must stay readable. */
    @Test
    void legacyPlaintextIsReturnedUnchanged() {
        assertThat(service().decrypt("legacy-plaintext-password")).isEqualTo("legacy-plaintext-password");
    }

    /** GCM authenticates the ciphertext: tampering must fail loudly, not decrypt to garbage. */
    @Test
    void tamperedCiphertextIsRejected() {
        CryptoService crypto = service();
        String encrypted = crypto.encrypt("hunter2");

        // Flip a character in the base64 body.
        char[] chars = encrypted.toCharArray();
        int idx = encrypted.length() - 3;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> crypto.decrypt(new String(chars)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A value encrypted under one key must not be readable with another. */
    @Test
    void aDifferentKeyCannotDecrypt() {
        String encrypted = service().encrypt("hunter2");
        CryptoService other = service("a-completely-different-secret-key-32-chars");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> service("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qaas.crypto.secret");
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> service("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 UTF-8 bytes");
    }

    @Test
    void isEncryptedDistinguishesStoredForms() {
        CryptoService crypto = service();

        assertThat(crypto.isEncrypted(crypto.encrypt("x"))).isTrue();
        assertThat(crypto.isEncrypted("legacy-plaintext")).isFalse();
        assertThat(crypto.isEncrypted(null)).isFalse();
    }
}
