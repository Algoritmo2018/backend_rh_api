package com.minhaempresa.recrutamentoapi.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets kept in the database (SMTP passwords) with AES-256-GCM: a random 12-byte IV per
 * value and a 128-bit authentication tag, so a changed or truncated value is detected instead of
 * decrypting to wrong text. Format {@code v1:<base64(iv | ciphertext | tag)>}.
 *
 * <p>Key rotation: set the new key in {@code APP_SECRETS_KEY} and the old one in
 * {@code APP_SECRETS_KEY_PREVIOUS}; values written with either key are read, new values always use the
 * current key. Invalid keys stop the application at startup.
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey current;
    private final List<SecretKey> decryptionKeys = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(SecretsProperties properties) {
        this.current = parse(properties.key(), "APP_SECRETS_KEY");
        if (current != null) {
            decryptionKeys.add(current);
        }
        SecretKey previous = parse(properties.previousKey(), "APP_SECRETS_KEY_PREVIOUS");
        if (previous != null) {
            decryptionKeys.add(previous);
        }
    }

    /** Whether secrets can be stored (a key is configured). */
    public boolean isConfigured() {
        return current != null;
    }

    public String encrypt(String plainText) {
        if (current == null) {
            throw new IllegalStateException("APP_SECRETS_KEY is not configured");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a secret", e);
        }
    }

    /** @throws IllegalStateException if no configured key opens the value (wrong key or tampered value) */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unknown secret format");
        }
        byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        if (data.length <= IV_BYTES) {
            throw new IllegalStateException("Truncated secret");
        }
        for (SecretKey key : decryptionKeys) {
            try {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
                return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
            } catch (GeneralSecurityException e) {
                // Try the next key (rotation); the error below never includes the value
            }
        }
        throw new IllegalStateException("A stored secret cannot be decrypted with the configured keys");
    }

    private static SecretKey parse(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(name + " must be 32 bytes (openssl rand -base64 32)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
