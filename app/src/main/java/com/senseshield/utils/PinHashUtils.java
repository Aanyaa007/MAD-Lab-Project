package com.senseshield.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PinHashUtils.java
 * One-way SHA-256 hashing for the caregiver PIN.
 * The raw PIN is never stored — only its hex-encoded hash.
 */
public final class PinHashUtils {

    private PinHashUtils() {}

    /**
     * Returns the SHA-256 hex string of {@code pin}.
     * SHA-256 is always available on Android; the catch block is a safety net.
     */
    public static String hash(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Should never happen on Android — SHA-256 is always available
            return pin;
        }
    }

    /**
     * Returns true if {@code pin} hashes to {@code storedHash}.
     */
    public static boolean verify(String pin, String storedHash) {
        if (pin == null || storedHash == null || storedHash.isEmpty()) return false;
        return storedHash.equals(hash(pin));
    }
}
