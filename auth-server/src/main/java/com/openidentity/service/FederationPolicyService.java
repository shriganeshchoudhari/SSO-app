package com.openidentity.service;

import com.openidentity.domain.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@ApplicationScoped
public class FederationPolicyService {
  public boolean isFederated(UserEntity user) {
    return user != null && user.getFederationSource() != null && !user.getFederationSource().isBlank();
  }

  public boolean isLdapManaged(UserEntity user) {
    return isFederated(user) && "ldap".equalsIgnoreCase(user.getFederationSource());
  }

  public boolean isOidcManaged(UserEntity user) {
    return isFederated(user) && "oidc".equalsIgnoreCase(user.getFederationSource());
  }

  public boolean isSamlManaged(UserEntity user) {
    return isFederated(user) && "saml".equalsIgnoreCase(user.getFederationSource());
  }

  public void ensureLocalPasswordAllowed(UserEntity user) {
    if (isLdapManaged(user)) {
      throw new WebApplicationException("ldap_managed_user_password_read_only", Response.Status.CONFLICT);
    }
    if (isOidcManaged(user)) {
      throw new WebApplicationException("oidc_managed_user_password_read_only", Response.Status.CONFLICT);
    }
    if (isSamlManaged(user)) {
      throw new WebApplicationException("saml_managed_user_password_read_only", Response.Status.CONFLICT);
    }
  }

  public void ensureEmailEditable(UserEntity user) {
    if (isLdapManaged(user)) {
      throw new WebApplicationException("ldap_managed_user_profile_read_only", Response.Status.CONFLICT);
    }
    if (isOidcManaged(user)) {
      throw new WebApplicationException("oidc_managed_user_profile_read_only", Response.Status.CONFLICT);
    }
    if (isSamlManaged(user)) {
      throw new WebApplicationException("saml_managed_user_profile_read_only", Response.Status.CONFLICT);
    }
  }

  public void markLdapManaged(UserEntity user, UUID providerId) {
    user.setFederationSource("ldap");
    user.setFederationProviderId(providerId);
  }

  public void markOidcManaged(UserEntity user, UUID providerId, String externalId) {
    user.setFederationSource("oidc");
    user.setFederationProviderId(providerId);
    user.setFederationExternalId(externalId);
  }

  public void markSamlManaged(UserEntity user, UUID providerId, String externalId) {
    user.setFederationSource("saml");
    user.setFederationProviderId(providerId);
    user.setFederationExternalId(externalId);
  }

  public void detachToLocal(UserEntity user) {
    user.setFederationSource(null);
    user.setFederationProviderId(null);
    user.setFederationExternalId(null);
  }
}
