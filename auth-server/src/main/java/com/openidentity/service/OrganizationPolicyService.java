package com.openidentity.service;

import com.openidentity.domain.OrganizationEntity;
import com.openidentity.domain.OrganizationMemberEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class OrganizationPolicyService {
  @Inject EntityManager em;

  public OrganizationEntity requireActiveOrganization(RealmEntity realm, String organizationHint) {
    String normalizedHint = normalizeNullable(organizationHint);
    if (normalizedHint == null) {
      return null;
    }
    OrganizationEntity organization =
        em.createQuery(
                "select o from OrganizationEntity o where o.realm.id = :realmId and o.enabled = true and o.name = :name",
                OrganizationEntity.class)
            .setParameter("realmId", realm.getId())
            .setParameter("name", normalizedHint)
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (organization == null) {
      throw new WebApplicationException("invalid_organization", Response.Status.BAD_REQUEST);
    }
    return organization;
  }

  public void enforceLocalLogin(RealmEntity realm, String organizationHint, UserEntity user) {
    OrganizationEntity organization = requireActiveOrganization(realm, organizationHint);
    if (organization == null) {
      return;
    }
    enforceMembership(organization, user);
    enforceEmailDomains(organization, user != null ? user.getEmail() : null);
  }

  public void enforceBrokerLogin(
      RealmEntity realm, String organizationHint, UserEntity existingUser, String email) {
    OrganizationEntity organization = requireActiveOrganization(realm, organizationHint);
    if (organization == null) {
      return;
    }
    if (Boolean.TRUE.equals(organization.getRequireMembershipForLogin())) {
      if (existingUser == null || !isMember(organization, existingUser)) {
        throw accessDenied();
      }
    }
    String effectiveEmail = existingUser != null ? existingUser.getEmail() : email;
    enforceEmailDomains(organization, effectiveEmail);
  }

  public String normalizeAllowedEmailDomains(List<String> domains) {
    List<String> normalized = parseAllowedEmailDomains(domains);
    return normalized.isEmpty() ? null : String.join(",", normalized);
  }

  public List<String> parseAllowedEmailDomains(String rawDomains) {
    if (rawDomains == null || rawDomains.isBlank()) {
      return List.of();
    }
    String[] tokens = rawDomains.split("[,;\\s]+");
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String token : tokens) {
      String domain = normalizeDomain(token);
      if (domain != null) {
        normalized.add(domain);
      }
    }
    return List.copyOf(normalized);
  }

  public List<String> parseAllowedEmailDomains(List<String> domains) {
    if (domains == null) {
      return List.of();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String domain : domains) {
      String normalizedDomain = normalizeDomain(domain);
      if (normalizedDomain != null) {
        normalized.add(normalizedDomain);
      }
    }
    return List.copyOf(normalized);
  }

  private void enforceMembership(OrganizationEntity organization, UserEntity user) {
    if (!Boolean.TRUE.equals(organization.getRequireMembershipForLogin())) {
      return;
    }
    if (user == null || !isMember(organization, user)) {
      throw accessDenied();
    }
  }

  private void enforceEmailDomains(OrganizationEntity organization, String email) {
    List<String> allowedDomains = parseAllowedEmailDomains(organization.getAllowedEmailDomainsRaw());
    if (allowedDomains.isEmpty()) {
      return;
    }
    String normalizedEmail = normalizeNullable(email);
    if (normalizedEmail == null) {
      throw accessDenied();
    }
    int atIndex = normalizedEmail.lastIndexOf('@');
    if (atIndex <= 0 || atIndex == normalizedEmail.length() - 1) {
      throw accessDenied();
    }
    String emailDomain = normalizedEmail.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    for (String allowedDomain : allowedDomains) {
      if (emailDomain.equals(allowedDomain) || emailDomain.endsWith("." + allowedDomain)) {
        return;
      }
    }
    throw accessDenied();
  }

  private boolean isMember(OrganizationEntity organization, UserEntity user) {
    return !em.createQuery(
            "select m from OrganizationMemberEntity m where m.organization.id = :organizationId and m.user.id = :userId",
            OrganizationMemberEntity.class)
        .setParameter("organizationId", organization.getId())
        .setParameter("userId", user.getId())
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  private String normalizeDomain(String domain) {
    String normalized = normalizeNullable(domain);
    if (normalized == null) {
      return null;
    }
    String value = normalized.startsWith("@") ? normalized.substring(1) : normalized;
    value = value.toLowerCase(Locale.ROOT);
    if (!value.contains(".") || value.startsWith(".") || value.endsWith(".")) {
      throw new WebApplicationException("invalid_email_domain", Response.Status.BAD_REQUEST);
    }
    return value;
  }

  private WebApplicationException accessDenied() {
    return new WebApplicationException("organization_access_denied", Response.Status.FORBIDDEN);
  }

  private String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
