package com.openidentity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "signing_key")
public class SigningKeyEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id")
  private RealmEntity realm;

  @Column(name = "key_id", nullable = false, unique = true)
  private String keyId;

  @Column(name = "algorithm", nullable = false)
  private String algorithm;

  /** AES-GCM encrypted private key PEM, stored via SecretProtectionService. */
  @Column(name = "private_key_enc", nullable = false, columnDefinition = "TEXT")
  private String privateKeyEnc;

  /** Plain-text public key PEM — safe to store unencrypted; served via JWKS. */
  @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
  private String publicKeyPem;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /** Set when this key is superseded by a rotation. Still served in JWKS for grace window. */
  @Column(name = "retired_at")
  private OffsetDateTime retiredAt;

  /** Optional hard expiry; null means no absolute expiry. */
  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public RealmEntity getRealm() { return realm; }
  public void setRealm(RealmEntity realm) { this.realm = realm; }

  public String getKeyId() { return keyId; }
  public void setKeyId(String keyId) { this.keyId = keyId; }

  public String getAlgorithm() { return algorithm; }
  public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

  public String getPrivateKeyEnc() { return privateKeyEnc; }
  public void setPrivateKeyEnc(String privateKeyEnc) { this.privateKeyEnc = privateKeyEnc; }

  public String getPublicKeyPem() { return publicKeyPem; }
  public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

  public OffsetDateTime getRetiredAt() { return retiredAt; }
  public void setRetiredAt(OffsetDateTime retiredAt) { this.retiredAt = retiredAt; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

  public boolean isActive() { return retiredAt == null; }
}
