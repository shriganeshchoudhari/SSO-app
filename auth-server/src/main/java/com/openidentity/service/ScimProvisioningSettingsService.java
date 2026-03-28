package com.openidentity.service;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimProvisioningSettingsEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class ScimProvisioningSettingsService {
  public static final String DEPROVISION_MODE_DISABLE = "disable";
  public static final String DEPROVISION_MODE_DELETE = "delete";

  @Inject EntityManager em;

  public ScimProvisioningSettingsEntity findByRealm(UUID realmId) {
    return em.createQuery(
            "select s from ScimProvisioningSettingsEntity s where s.realm.id = :realmId",
            ScimProvisioningSettingsEntity.class)
        .setParameter("realmId", realmId)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  public ScimProvisioningSettingsEntity currentOrDefault(RealmEntity realm) {
    ScimProvisioningSettingsEntity existing = findByRealm(realm.getId());
    if (existing != null) {
      return existing;
    }
    ScimProvisioningSettingsEntity defaults = new ScimProvisioningSettingsEntity();
    defaults.setRealm(realm);
    defaults.setDeprovisionMode(DEPROVISION_MODE_DISABLE);
    return defaults;
  }

  @Transactional
  public ScimProvisioningSettingsEntity upsert(RealmEntity realm, String deprovisionMode) {
    OffsetDateTime now = OffsetDateTime.now();
    String normalizedDeprovisionMode = normalizeDeprovisionMode(deprovisionMode);
    ScimProvisioningSettingsEntity settings = findByRealm(realm.getId());
    if (settings == null) {
      settings = new ScimProvisioningSettingsEntity();
      settings.setId(UUID.randomUUID());
      settings.setRealm(realm);
      settings.setCreatedAt(now);
      settings.setUpdatedAt(now);
      settings.setDeprovisionMode(normalizedDeprovisionMode);
      em.persist(settings);
    }
    settings.setDeprovisionMode(normalizedDeprovisionMode);
    settings.setUpdatedAt(now);
    return settings;
  }

  public String resolveDeprovisionMode(RealmEntity realm) {
    ScimProvisioningSettingsEntity settings = findByRealm(realm.getId());
    return settings != null
        ? normalizeDeprovisionMode(settings.getDeprovisionMode())
        : DEPROVISION_MODE_DISABLE;
  }

  public String normalizeDeprovisionMode(String deprovisionMode) {
    String normalized =
        deprovisionMode == null || deprovisionMode.isBlank()
            ? DEPROVISION_MODE_DISABLE
            : deprovisionMode.trim().toLowerCase(Locale.ROOT);
    if (!DEPROVISION_MODE_DISABLE.equals(normalized)
        && !DEPROVISION_MODE_DELETE.equals(normalized)) {
      throw new BadRequestException("deprovisionMode must be 'disable' or 'delete'");
    }
    return normalized;
  }
}
