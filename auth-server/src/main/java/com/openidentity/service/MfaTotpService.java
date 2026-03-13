package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class MfaTotpService {

  private static final String HMAC_ALGO = "HmacSHA1";
  private static final int DEFAULT_DIGITS = 6;
  private static final int STEP_SECONDS = 30;
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  public String generateSecret() {
    byte[] buf = new byte[20];
    RANDOM.nextBytes(buf);
    return base32Encode(buf);
  }

  public boolean verify(String base32Secret, String code) {
    if (base32Secret == null || base32Secret.isBlank() || code == null || code.isBlank()) {
      return false;
    }
    String normalized = code.replace(" ", "");
    if (!normalized.matches("^\\d{6}$")) {
      return false;
    }
    long nowSeconds = System.currentTimeMillis() / 1000L;
    long timeStep = nowSeconds / STEP_SECONDS;
    // Allow a small window to tolerate clock skew
    for (int offset = -1; offset <= 1; offset++) {
      long counter = timeStep + offset;
      String expected = totp(base32Secret, counter);
      if (expected.equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  public String buildProvisioningUri(String secret, String issuer, String accountName) {
    // otpauth://totp/Issuer:account?secret=XXX&issuer=Issuer&algorithm=SHA1&digits=6&period=30
    String label = urlEncode(issuer) + ":" + urlEncode(accountName);
    StringBuilder sb = new StringBuilder("otpauth://totp/");
    sb.append(label)
        .append("?secret=").append(secret)
        .append("&issuer=").append(urlEncode(issuer))
        .append("&algorithm=SHA1&digits=").append(DEFAULT_DIGITS)
        .append("&period=").append(STEP_SECONDS);
    return sb.toString();
  }

  private String totp(String base32Secret, long counter) {
    try {
      byte[] key = base32Decode(base32Secret);
      byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(key, HMAC_ALGO));
      byte[] hmac = mac.doFinal(counterBytes);
      int offset = hmac[hmac.length - 1] & 0x0F;
      int binary =
          ((hmac[offset] & 0x7f) << 24)
              | ((hmac[offset + 1] & 0xff) << 16)
              | ((hmac[offset + 2] & 0xff) << 8)
              | (hmac[offset + 3] & 0xff);
      int otp = binary % (int) Math.pow(10, DEFAULT_DIGITS);
      return String.format("%0" + DEFAULT_DIGITS + "d", otp);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to compute TOTP", e);
    }
  }

  private String base32Encode(byte[] data) {
    StringBuilder result = new StringBuilder();
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer <<= 8;
      buffer |= (b & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        int index = (buffer >> (bitsLeft - 5)) & 0x1f;
        bitsLeft -= 5;
        result.append(BASE32_ALPHABET[index]);
      }
    }
    if (bitsLeft > 0) {
      int index = (buffer << (5 - bitsLeft)) & 0x1f;
      result.append(BASE32_ALPHABET[index]);
    }
    return result.toString();
  }

  private byte[] base32Decode(String s) {
    String input = s.trim().replace(" ", "").toUpperCase();
    int buffer = 0;
    int bitsLeft = 0;
    byte[] out = new byte[input.length() * 5 / 8];
    int count = 0;
    for (char c : input.toCharArray()) {
      int val = charToBase32(c);
      if (val < 0) continue;
      buffer <<= 5;
      buffer |= val;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out[count++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    byte[] result = new byte[count];
    System.arraycopy(out, 0, result, 0, count);
    return result;
  }

  private int charToBase32(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= '2' && c <= '7') return 26 + (c - '2');
    return -1;
  }

  private String urlEncode(String s) {
    try {
      return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name())
          .replace("+", "%20");
    } catch (Exception e) {
      return s;
    }
  }
}

