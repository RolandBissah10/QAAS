package com.qaas.security.crypto;

import com.qaas.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the JPA boundary: what actually lands in, and comes back from, the column. */
class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;
    private CryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new CryptoService(new AppProperties(
                new AppProperties.Jwt("jwt-secret-at-least-32-characters-long!!", 30, 14),
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Mail(false, "noreply@qaas.local"),
                new AppProperties.Crypto("test-crypto-secret-at-least-32-characters-long")));
        converter = new EncryptedStringConverter();
        converter.setCryptoService(crypto);
    }

    @AfterEach
    void tearDown() {
        EncryptedStringConverter.overrideCryptoService(null);
    }

    @Test
    void valueIsEncryptedOnTheWayToTheColumn() {
        String stored = converter.convertToDatabaseColumn("hunter2");

        assertThat(stored).doesNotContain("hunter2").startsWith("enc:v1:");
    }

    @Test
    void valueIsDecryptedOnTheWayBack() {
        String stored = converter.convertToDatabaseColumn("hunter2");

        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("hunter2");
    }

    /** Re-saving an entity must not encrypt an already-encrypted value a second time. */
    @Test
    void encryptionIsIdempotent() {
        String once = converter.convertToDatabaseColumn("hunter2");
        String twice = converter.convertToDatabaseColumn(once);

        assertThat(twice).isEqualTo(once);
        assertThat(converter.convertToEntityAttribute(twice)).isEqualTo("hunter2");
    }

    /** Rows written before this converter existed must still read back. */
    @Test
    void legacyPlaintextColumnStillReads() {
        assertThat(converter.convertToEntityAttribute("old-plaintext")).isEqualTo("old-plaintext");
    }

    @Test
    void nullsSurviveBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    /** Without a Spring context the converter must pass through, not blow up. */
    @Test
    void passesThroughWhenNoServiceIsConfigured() {
        EncryptedStringConverter.overrideCryptoService(null);

        assertThat(converter.convertToDatabaseColumn("plain")).isEqualTo("plain");
        assertThat(converter.convertToEntityAttribute("plain")).isEqualTo("plain");
    }

    /** The ciphertext must fit the column the entity declares (2048). */
    @Test
    void ciphertextFitsTheColumnForARealisticPassword() {
        String longPassword = "x".repeat(256);

        assertThat(converter.convertToDatabaseColumn(longPassword).length()).isLessThan(2048);
    }
}
