package com.bookassistant.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts OAuth tokens at rest using AES-GCM.
 * <p>
 * The encryption key is expected as a base64-encoded 256-bit value in the
 * {@code app.encryption-key} property. If no key is provided, the service fails
 * to initialize so that plaintext tokens are never persisted by mistake.
 */
@Service
public class TokenEncryptionService {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    @Value("${app.encryption-key:}")
    private String encryptionKeyB64;

    private SecretKeySpec secretKey;

    @PostConstruct
    public void init() {
        if (encryptionKeyB64 == null || encryptionKeyB64.isBlank()) {
            throw new IllegalStateException(
                "app.encryption-key is required. Set a base64-encoded 256-bit key (e.g. APP_ENCRYPTION_KEY).");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyB64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.encryption-key must decode to exactly 32 bytes (256 bits).");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a plaintext value. Returns {@code null} if the input is {@code null}.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[GCM_IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt token", e);
        }
    }

    /**
     * Decrypts an encrypted value produced by {@link #encrypt(String)}.
     * Returns {@code null} if the input is {@code null}.
     * <p>
     * Decryption failures are not swallowed: a corrupted or tampered ciphertext
     * throws an exception so the caller is forced to handle it rather than
     * persisting or returning the raw value.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        byte[] data = Base64.getDecoder().decode(ciphertext);
        if (data.length <= GCM_IV_BYTES) {
            throw new IllegalArgumentException("Ciphertext too short to contain an IV and tag");
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(data, 0, iv, 0, GCM_IV_BYTES);
            byte[] ct = new byte[data.length - GCM_IV_BYTES];
            System.arraycopy(data, GCM_IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt token", e);
        }
    }
}
