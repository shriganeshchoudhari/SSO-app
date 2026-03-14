package com.openidentity.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class JwtKeyService {
  @ConfigProperty(name = "openidentity.jwt.private-key-pem")
  Optional<String> configuredPrivateKeyPem;

  @ConfigProperty(name = "openidentity.jwt.public-key-pem")
  Optional<String> configuredPublicKeyPem;

  @ConfigProperty(name = "openidentity.jwt.key-id", defaultValue = "openidentity-rs256")
  String keyId;

  private PrivateKey privateKey;
  private RSAPublicKey publicKey;

  @PostConstruct
  void init() {
    try {
      String privateKeyPem = configuredPrivateKeyPem.orElse("").trim();
      String publicKeyPem = configuredPublicKeyPem.orElse("").trim();
      if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
        publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodePem(publicKeyPem)));
        return;
      }

      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();
      privateKey = keyPair.getPrivate();
      publicKey = (RSAPublicKey) keyPair.getPublic();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize JWT signing keys", e);
    }
  }

  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public String getKeyId() {
    return keyId;
  }

  public String getAlgorithm() {
    return "RS256";
  }

  public Map<String, Object> asJwk() {
    Map<String, Object> key = new HashMap<>();
    key.put("kty", "RSA");
    key.put("use", "sig");
    key.put("alg", getAlgorithm());
    key.put("kid", keyId);
    key.put("n", base64UrlUInt(publicKey.getModulus()));
    key.put("e", base64UrlUInt(publicKey.getPublicExponent()));
    return key;
  }

  private byte[] decodePem(String pem) {
    String normalized = pem
        .replace("\r", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\n", "")
        .trim();
    return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
  }

  private String base64UrlUInt(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      bytes = trimmed;
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
