package com.minhaempresa.recrutamentoapi.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Generation and hashing of secrets that are stored only as hashes.
 *
 * <ul>
 *   <li>High-entropy tokens (links, refresh tokens): SHA-256 is enough.
 *   <li>6-digit codes: HMAC-SHA256 with a server-side key, so a leaked database does not allow trying
 *       the million possible codes offline.
 * </ul>
 */
@Component
public class TokenHasher {

    private static final int TOKEN_BYTES = 32;
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec pepper;

    public TokenHasher(AuthProperties properties) {
        this.pepper = new SecretKeySpec(properties.tokenPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 256-bit random token, URL-safe Base64 without padding. */
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Random 6-digit code ({@code 000000}–{@code 999999}). */
    public String newCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    public String sha256(String value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepper);
            return HEX.formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of two hex hashes. */
    public boolean matches(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII), actualHash.getBytes(StandardCharsets.US_ASCII));
    }
}
