package com.openidentity.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

public final class SecurityTokenService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private SecurityTokenService() {}

  public static String generateToken() {
    byte[] b = new byte[32];
    RANDOM.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte d : digest) {
        sb.append(String.format("%02x", d));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public static OffsetDateTime expiresIn(Duration ttl) {
    return OffsetDateTime.now().plus(ttl);
  }
}

