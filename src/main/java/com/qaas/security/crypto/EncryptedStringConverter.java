package com.qaas.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts a String column at rest via {@link CryptoService}.
 *
 * <p>Hibernate may instantiate converters itself rather than taking them from the Spring
 * context, so the service is held in a static field populated once at startup. The instance
 * field exists only so Spring has somewhere to inject.
 *
 * <p>If no context has started (plain unit tests that touch entities without booting Spring),
 * the converter passes values through unchanged rather than failing — encryption at rest is
 * a persistence concern and must not make the entity unusable in isolation.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile CryptoService cryptoService;

    @Autowired
    public void setCryptoService(CryptoService service) {
        EncryptedStringConverter.cryptoService = service;
    }

    /** For tests that need to drive the converter without a Spring context. */
    static void overrideCryptoService(CryptoService service) {
        EncryptedStringConverter.cryptoService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        CryptoService service = cryptoService;
        if (service == null || attribute == null) {
            return attribute;
        }
        // Idempotent: never double-encrypt a value already in encrypted form.
        return service.isEncrypted(attribute) ? attribute : service.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        CryptoService service = cryptoService;
        if (service == null || dbData == null) {
            return dbData;
        }
        return service.decrypt(dbData);
    }
}
