package com.bookassistant.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA {@link AttributeConverter} that transparently encrypts OAuth token strings
 * at rest using {@link TokenEncryptionService}.
 * <p>
 * Database columns store ciphertext; entity getters return plaintext.
 */
@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final TokenEncryptionService tokenEncryptionService;

    @Autowired
    public EncryptedStringConverter(TokenEncryptionService tokenEncryptionService) {
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return tokenEncryptionService.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        return tokenEncryptionService.decrypt(ciphertext);
    }
}
