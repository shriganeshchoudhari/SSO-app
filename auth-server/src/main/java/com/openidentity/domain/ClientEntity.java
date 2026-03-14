package com.openidentity.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "client")
public class ClientEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @Column(name = "client_id", nullable = false)
  private String clientId;

  @Column(name = "protocol", nullable = false)
  private String protocol;

  @Column(name = "secret")
  private String secret;

  @Column(name = "redirect_uris_raw", length = 4000)
  private String redirectUrisRaw;

  @Column(name = "grant_types_raw", length = 1000)
  private String grantTypesRaw;

  @Column(name = "public_client")
  private Boolean publicClient = Boolean.FALSE;

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
  public String getClientId() {
    return clientId;
  }
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }
  public String getProtocol() {
    return protocol;
  }
  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }
  public String getSecret() {
    return secret;
  }
  public void setSecret(String secret) {
    this.secret = secret;
  }
  public String getRedirectUrisRaw() {
    return redirectUrisRaw;
  }
  public void setRedirectUrisRaw(String redirectUrisRaw) {
    this.redirectUrisRaw = redirectUrisRaw;
  }
  public List<String> getRedirectUris() {
    return splitValues(redirectUrisRaw);
  }
  public void setRedirectUris(List<String> redirectUris) {
    this.redirectUrisRaw = joinValues(redirectUris);
  }
  public String getGrantTypesRaw() {
    return grantTypesRaw;
  }
  public void setGrantTypesRaw(String grantTypesRaw) {
    this.grantTypesRaw = grantTypesRaw;
  }
  public Set<String> getGrantTypes() {
    return new LinkedHashSet<>(splitValues(grantTypesRaw));
  }
  public void setGrantTypes(List<String> grantTypes) {
    this.grantTypesRaw = joinValues(grantTypes);
  }
  public Boolean getPublicClient() {
    return publicClient;
  }
  public void setPublicClient(Boolean publicClient) {
    this.publicClient = publicClient;
  }

  private static List<String> splitValues(String raw) {
    List<String> values = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return values;
    }
    for (String part : raw.split("\\r?\\n|,")) {
      String trimmed = part.trim();
      if (!trimmed.isBlank()) {
        values.add(trimmed);
      }
    }
    return values;
  }

  private static String joinValues(List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    List<String> normalized = new ArrayList<>();
    for (String value : values) {
      if (value != null) {
        String trimmed = value.trim();
        if (!trimmed.isBlank()) {
          normalized.add(trimmed);
        }
      }
    }
    return normalized.isEmpty() ? null : String.join("\n", normalized);
  }
}
