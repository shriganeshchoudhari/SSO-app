package com.openidentity.service;

import com.openidentity.domain.SigningKeyEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Manages RS256 signing keys with DB persistence and rotation support.
 *
 * <p>Initialization can happen lazily on a request path, so first-use generation must explicitly
 * run inside a transaction instead of relying on lifecycle interception.
 */
@ApplicationScoped
public class JwtKeyService {

  private static final Logger LOG = Logger.getLogger(JwtKeyService.class);
  private static final int RETIRED_KEY_GRACE_HOURS = 24;

  @ConfigProperty(name = "openidentity.jwt.private-key-pem")
  Optional<String> configuredPrivateKeyPem;

  @ConfigProperty(name = "openidentity.jwt.public-key-pem")
  Optional<String> configuredPublicKeyPem;

  @ConfigProperty(name = "openidentity.jwt.key-id", defaultValue = "openidentity-rs256")
  String configuredKeyId;

  @Inject EntityManager em;
  @Inject SecretProtectionService secretProtectionService;

  private PrivateKey activePrivateKey;
  private RSAPublicKey activePublicKey;
  private String activeKeyId;

  @PostConstruct
  void init() {
    initializeIfNeeded();
  }

  private synchronized void initializeIfNeeded() {
    if (activePrivateKey != null && activePublicKey != null && activeKeyId != null) {
      return;
    }

    String privatePem = configuredPrivateKeyPem.orElse("").trim();
    String publicPem = configuredPublicKeyPem.orElse("").trim();
    if (!privatePem.isBlank() && !publicPem.isBlank()) {
      loadFromPem(privatePem, publicPem, configuredKeyId);
      LOG.info("JWT signing key loaded from environment config (stateless override).");
      return;
    }

    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              List<SigningKeyEntity> active =
                  em.createQuery(
                          "select k from SigningKeyEntity k where k.retiredAt is null order by k.createdAt desc",
                          SigningKeyEntity.class)
                      .setMaxResults(1)
                      .getResultList();
              if (!active.isEmpty()) {
                loadFromEntity(active.get(0));
                LOG.infof("JWT signing key loaded from DB: kid=%s", activeKeyId);
                return;
              }

              SigningKeyEntity entity = generateAndPersist();
              loadFromEntity(entity);
              LOG.infof("JWT signing key generated and persisted: kid=%s", activeKeyId);
            });
  }

  public PrivateKey getPrivateKey() {
    initializeIfNeeded();
    return activePrivateKey;
  }

  public PublicKey getPublicKey() {
    initializeIfNeeded();
    return activePublicKey;
  }

  public String getKeyId() {
    initializeIfNeeded();
    return activeKeyId;
  }

  public String getAlgorithm() {
    return "RS256";
  }

  public Map<String, Object> asJwk() {
    initializeIfNeeded();
    return buildJwk(activePublicKey, activeKeyId);
  }

  public List<Map<String, Object>> allJwks() {
    initializeIfNeeded();
    List<Map<String, Object>> keys = new ArrayList<>();
    keys.add(asJwk());

    OffsetDateTime graceThreshold = OffsetDateTime.now().minusHours(RETIRED_KEY_GRACE_HOURS);
    List<SigningKeyEntity> retired =
        em.createQuery(
                "select k from SigningKeyEntity k where k.retiredAt is not null and k.retiredAt > :threshold order by k.retiredAt desc",
                SigningKeyEntity.class)
            .setParameter("threshold", graceThreshold)
            .getResultList();

    for (SigningKeyEntity key : retired) {
      try {
        RSAPublicKey pub = parsePublicKey(key.getPublicKeyPem());
        keys.add(buildJwk(pub, key.getKeyId()));
      } catch (Exception e) {
        LOG.warnf("Could not parse retired key %s for JWKS: %s", key.getKeyId(), e.getMessage());
      }
    }
    return keys;
  }

  @Transactional
  public SigningKeyEntity rotate() {
    em.createQuery(
            "select k from SigningKeyEntity k where k.retiredAt is null", SigningKeyEntity.class)
        .getResultList()
        .forEach(
            key -> {
              key.setRetiredAt(OffsetDateTime.now());
              em.merge(key);
            });

    SigningKeyEntity next = generateAndPersist();
    loadFromEntity(next);
    LOG.infof("JWT signing key rotated. New kid=%s", activeKeyId);
    return next;
  }

  private SigningKeyEntity generateAndPersist() {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair pair = gen.generateKeyPair();

      String kid = "oi-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
      String privatePem = encodePem("PRIVATE KEY", pair.getPrivate().getEncoded());
      String publicPem = encodePem("PUBLIC KEY", pair.getPublic().getEncoded());
      String encPrivate = secretProtectionService.protectOpaqueSecret(privatePem);

      SigningKeyEntity entity = new SigningKeyEntity();
      entity.setId(UUID.randomUUID());
      entity.setKeyId(kid);
      entity.setAlgorithm("RS256");
      entity.setPrivateKeyEnc(encPrivate);
      entity.setPublicKeyPem(publicPem);
      entity.setCreatedAt(OffsetDateTime.now());
      em.persist(entity);
      return entity;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate JWT signing key pair", e);
    }
  }

  private void loadFromEntity(SigningKeyEntity entity) {
    try {
      String privatePem = secretProtectionService.revealOpaqueSecret(entity.getPrivateKeyEnc());
      loadFromPem(privatePem, entity.getPublicKeyPem(), entity.getKeyId());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load JWT signing key from DB entity", e);
    }
  }

  private void loadFromPem(String privatePem, String publicPem, String kid) {
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      activePrivateKey =
          keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem)));
      activePublicKey =
          (RSAPublicKey)
              keyFactory.generatePublic(new X509EncodedKeySpec(decodePem(publicPem)));
      activeKeyId = kid;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse RSA key PEMs", e);
    }
  }

  private RSAPublicKey parsePublicKey(String pem) throws Exception {
    return (RSAPublicKey)
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(decodePem(pem)));
  }

  private Map<String, Object> buildJwk(RSAPublicKey pub, String kid) {
    Map<String, Object> key = new HashMap<>();
    key.put("kty", "RSA");
    key.put("use", "sig");
    key.put("alg", "RS256");
    key.put("kid", kid);
    key.put("n", base64UrlUInt(pub.getModulus()));
    key.put("e", base64UrlUInt(pub.getPublicExponent()));
    return key;
  }

  private byte[] decodePem(String pem) {
    String normalized =
        pem.replace("\r", "")
            .replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replace("\n", "")
            .trim();
    return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
  }

  private String encodePem(String type, byte[] der) {
    String b64 = Base64.getEncoder().encodeToString(der);
    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN ").append(type).append("-----\n");
    for (int i = 0; i < b64.length(); i += 64) {
      sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
    }
    sb.append("-----END ").append(type).append("-----");
    return sb.toString();
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
