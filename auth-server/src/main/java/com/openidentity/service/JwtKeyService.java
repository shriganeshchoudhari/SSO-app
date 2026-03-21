package com.openidentity.service;

import com.openidentity.domain.SigningKeyEntity;
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
import java.security.interfaces.RSAPrivateKey;
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
 * Manages RS256 signing key pairs with database persistence and rotation support.
 *
 * <p>On startup the service loads the active key from the {@code signing_key} table. If none
 * exists it generates a fresh 2048-bit RSA pair, persists it, and uses it. External PEM config
 * (OPENIDENTITY_JWT_PRIVATE_KEY_PEM / OPENIDENTITY_JWT_PUBLIC_KEY_PEM) is still supported for
 * environments that inject keys via secrets management; when provided they are used as the active
 * key but are NOT written to the database (stateless override mode).
 *
 * <p>On rotation the current active key is retired (retired_at stamped) and a new pair is
 * generated and persisted. The JWKS endpoint serves both active and recently-retired keys so that
 * tokens issued before the rotation remain verifiable for their remaining lifetime.
 */
@ApplicationScoped
public class JwtKeyService {

  private static final Logger LOG = Logger.getLogger(JwtKeyService.class);
  /** Keep retired keys in JWKS for this many hours so live tokens remain verifiable. */
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

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  @PostConstruct
  @Transactional
  void init() {
    String privatePem = configuredPrivateKeyPem.orElse("").trim();
    String publicPem  = configuredPublicKeyPem.orElse("").trim();

    if (!privatePem.isBlank() && !publicPem.isBlank()) {
      // Stateless override via environment/secrets — use as-is, skip DB.
      loadFromPem(privatePem, publicPem, configuredKeyId);
      LOG.info("JWT signing key loaded from environment config (stateless override).");
      return;
    }

    // Try to load the current active key from the database.
    List<SigningKeyEntity> active = em.createQuery(
            "select k from SigningKeyEntity k where k.retiredAt is null order by k.createdAt desc",
            SigningKeyEntity.class)
        .setMaxResults(1)
        .getResultList();

    if (!active.isEmpty()) {
      loadFromEntity(active.get(0));
      LOG.infof("JWT signing key loaded from DB: kid=%s", activeKeyId);
    } else {
      // First boot — generate, persist, and activate.
      SigningKeyEntity entity = generateAndPersist(null);
      loadFromEntity(entity);
      LOG.infof("JWT signing key generated and persisted: kid=%s", activeKeyId);
    }
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  public PrivateKey getPrivateKey() { return activePrivateKey; }
  public PublicKey  getPublicKey()  { return activePublicKey; }
  public String     getKeyId()      { return activeKeyId; }
  public String     getAlgorithm()  { return "RS256"; }

  /** Returns JWK map for the current active key. */
  public Map<String, Object> asJwk() {
    return buildJwk(activePublicKey, activeKeyId);
  }

  /**
   * Returns JWK maps for all keys that should be advertised — the active key plus any
   * recently-retired keys still within the grace window.
   */
  public List<Map<String, Object>> allJwks() {
    List<Map<String, Object>> keys = new ArrayList<>();
    keys.add(asJwk());

    OffsetDateTime graceThreshold = OffsetDateTime.now().minusHours(RETIRED_KEY_GRACE_HOURS);
    List<SigningKeyEntity> retired = em.createQuery(
            "select k from SigningKeyEntity k where k.retiredAt is not null and k.retiredAt > :threshold order by k.retiredAt desc",
            SigningKeyEntity.class)
        .setParameter("threshold", graceThreshold)
        .getResultList();

    for (SigningKeyEntity k : retired) {
      try {
        RSAPublicKey pub = parsePublicKey(k.getPublicKeyPem());
        keys.add(buildJwk(pub, k.getKeyId()));
      } catch (Exception e) {
        LOG.warnf("Could not parse retired key %s for JWKS: %s", k.getKeyId(), e.getMessage());
      }
    }
    return keys;
  }

  /**
   * Rotates the active signing key: retires the current active key in the DB, generates a new
   * pair, persists it, and activates it in-memory immediately.
   *
   * @return the new key entity
   */
  @Transactional
  public SigningKeyEntity rotate() {
    // Retire all currently active keys (should be just one, but be safe).
    em.createQuery("select k from SigningKeyEntity k where k.retiredAt is null", SigningKeyEntity.class)
        .getResultList()
        .forEach(k -> {
          k.setRetiredAt(OffsetDateTime.now());
          em.merge(k);
        });

    SigningKeyEntity next = generateAndPersist(null);
    loadFromEntity(next);
    LOG.infof("JWT signing key rotated. New kid=%s", activeKeyId);
    return next;
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private SigningKeyEntity generateAndPersist(RealmScope scope) {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair pair = gen.generateKeyPair();

      String kid        = "oi-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
      String privatePem = encodePem("PRIVATE KEY", pair.getPrivate().getEncoded());
      String publicPem  = encodePem("PUBLIC KEY",  pair.getPublic().getEncoded());
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
      KeyFactory kf = KeyFactory.getInstance("RSA");
      activePrivateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem)));
      activePublicKey  = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(decodePem(publicPem)));
      activeKeyId      = kid;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse RSA key PEMs", e);
    }
  }

  private RSAPublicKey parsePublicKey(String pem) throws Exception {
    return (RSAPublicKey) KeyFactory.getInstance("RSA")
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
    String normalized = pem.replace("\r", "")
        .replaceAll("-----BEGIN [^-]+-----", "")
        .replaceAll("-----END [^-]+-----", "")
        .replace("\n", "").trim();
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

  /** Placeholder for future per-realm scope argument. */
  private static final class RealmScope {}
}
