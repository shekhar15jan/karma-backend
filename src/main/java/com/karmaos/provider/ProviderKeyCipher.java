package com.karmaos.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class ProviderKeyCipher {

    private static final byte FORMAT_MAGIC = 0x01;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ProviderKeyCipher(@Value("${provider-key-secret:${PROVIDER_KEY_SECRET:}}") String secret) {
        String material = (secret == null || secret.isBlank())
                ? "karma-default-provider-key-secret-change-me"
                : secret;
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[1 + IV_LENGTH + cipherText.length];
            result[0] = FORMAT_MAGIC;
            System.arraycopy(iv, 0, result, 1, IV_LENGTH);
            System.arraycopy(cipherText, 0, result, 1 + IV_LENGTH, cipherText.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt provider key", e);
        }
    }

    public boolean isEncrypted(byte[] stored) {
        return stored != null && stored.length > 0 && stored[0] == FORMAT_MAGIC;
    }

    public String decrypt(byte[] stored) {
        if (stored == null || stored.length == 0) {
            return "";
        }
        if (stored[0] != FORMAT_MAGIC) {
            return new String(stored, StandardCharsets.UTF_8);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(stored, 1, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[stored.length - 1 - IV_LENGTH];
            System.arraycopy(stored, 1 + IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
