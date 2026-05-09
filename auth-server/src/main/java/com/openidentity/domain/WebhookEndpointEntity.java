package com.openidentity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpointEntity {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "url", nullable = false, length = 2000)
  private String url;

  @Column(name = "signing_secret")
  private String signingSecret;

  @Column(name = "subscribed_events_raw", length = 2000)
  private String subscribedEventsRaw;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_delivery_at")
  private OffsetDateTime lastDeliveryAt;

  @Column(name = "last_failure_at")
  private OffsetDateTime lastFailureAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public RealmEntity getRealm() {
    return realm;
  }

  public void setRealm(RealmEntity realm) {
    this.realm = realm;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getSigningSecret() {
    return signingSecret;
  }

  public void setSigningSecret(String signingSecret) {
    this.signingSecret = signingSecret;
  }

  public String getSubscribedEventsRaw() {
    return subscribedEventsRaw;
  }

  public void setSubscribedEventsRaw(String subscribedEventsRaw) {
    this.subscribedEventsRaw = subscribedEventsRaw;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getLastDeliveryAt() {
    return lastDeliveryAt;
  }

  public void setLastDeliveryAt(OffsetDateTime lastDeliveryAt) {
    this.lastDeliveryAt = lastDeliveryAt;
  }

  public OffsetDateTime getLastFailureAt() {
    return lastFailureAt;
  }

  public void setLastFailureAt(OffsetDateTime lastFailureAt) {
    this.lastFailureAt = lastFailureAt;
  }
}
