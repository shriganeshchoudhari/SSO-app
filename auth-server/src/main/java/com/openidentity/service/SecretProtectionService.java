package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class SecretProtectionService {
  private static final String TOTP_PREFIX = "enc:";
  private static final int IV_LENGTH = 12;
  private static final int GCM_TAG_BITS = 128;

  @ConfigProperty(name = "openidentity.secret-protection.key")
  Optional<String> configuredKey;

  @ConfigProperty(name = "smallrye.jwt.sign.key")
  Optional<String> fallbackKey;

  private final SecureRandom random = new SecureRandom();

  public String hashClientSecret(String secret) {
    if (secret == null || secret.isBlank()) {
      return null;
    }
    return BCrypt.hashpw(secret, BCrypt.gensalt(12));
  }

  public boolean verifyClientSecret(String presentedSecret, String storedHash) {
    if (presentedSecret == null || presentedSecret.isBlank() || storedHash == null || storedHash.isBlank()) {
      return false;
    }
    return BCrypt.checkpw(presentedSecret, storedHash);
  }

  public String protectTotpSecret(String secret) {
    if (secret == null || secret.isBlank()) {
      return secret;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
      return TOTP_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to protect TOTP secret", e);
    }
  }

  public String revealTotpSecret(String storedValue) {
    if (storedValue == null || storedValue.isBlank()) {
      return storedValue;
    }
    if (!storedValue.startsWith(TOTP_PREFIX)) {
      return storedValue;
    }
    try {
      byte[] payload = Base64.getUrlDecoder().decode(storedValue.substring(TOTP_PREFIX.length()));
      byte[] iv = new byte[IV_LENGTH];
      byte[] ciphertext = new byte[payload.length - IV_LENGTH];
      System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
      System.arraycopy(payload, IV_LENGTH, ciphertext, 0, ciphertext.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to reveal TOTP secret", e);
    }
  }

  private SecretKeySpec encryptionKey() {
    String configured = configuredKey.orElse("");
    String fallback = fallbackKey.orElse("");
    String keyMaterial = !configured.isBlank() ? configured : fallback;
    if (keyMaterial == null || keyMaterial.isBlank()) {
      throw new IllegalStateException("No secret protection key configured");
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(digest, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to derive encryption key", e);
    }
  }
}
