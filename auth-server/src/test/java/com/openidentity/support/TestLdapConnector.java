package com.openidentity.support;

import com.openidentity.domain.LdapProviderEntity;
import com.openidentity.service.LdapConnector;
import com.openidentity.service.LdapFederationService;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

@Mock
@ApplicationScoped
public class TestLdapConnector implements LdapConnector {
  @Override
  public LdapFederationService.LdapAuthenticationOutcome authenticate(
      LdapProviderEntity provider,
      String username,
      String password,
      String bindCredential) {
    if (!Boolean.TRUE.equals(provider.getEnabled())) {
      return LdapFederationService.LdapAuthenticationOutcome.unavailable();
    }
    if ("missing-users".equals(provider.getUserSearchBase())) {
      return LdapFederationService.LdapAuthenticationOutcome.userNotFound();
    }
    if ("ldap://mock-directory".equals(provider.getUrl())
        && "ldapuser".equals(username)
        && "DirectorySecret123!".equals(password)) {
      return LdapFederationService.LdapAuthenticationOutcome.authenticated(
          new LdapFederationService.LdapDirectoryUser("ldapuser", "ldapuser@example.com"));
    }
    if ("ldap://mock-directory".equals(provider.getUrl())
        && "existing-ldap-user".equals(username)
        && "ExistingDirectorySecret123!".equals(password)) {
      return LdapFederationService.LdapAuthenticationOutcome.authenticated(
          new LdapFederationService.LdapDirectoryUser("existing-ldap-user", "existing-ldap@example.com"));
    }
    return LdapFederationService.LdapAuthenticationOutcome.invalidCredentials();
  }

  @Override
  public LdapFederationService.LdapLookupOutcome lookupUser(
      LdapProviderEntity provider,
      String username,
      String bindCredential) {
    if (!Boolean.TRUE.equals(provider.getEnabled())) {
      return LdapFederationService.LdapLookupOutcome.unavailable();
    }
    if ("missing-users".equals(provider.getUserSearchBase())) {
      return LdapFederationService.LdapLookupOutcome.notFound();
    }
    if ("ldap://mock-directory".equals(provider.getUrl()) && "ldapuser".equals(username)) {
      return LdapFederationService.LdapLookupOutcome.found(
          new LdapFederationService.LdapDirectoryUser("ldapuser", "ldapuser@example.com"),
          "uid=ldapuser,ou=users,dc=example,dc=com");
    }
    if ("ldap://mock-directory".equals(provider.getUrl()) && "existing-ldap-user".equals(username)) {
      return LdapFederationService.LdapLookupOutcome.found(
          new LdapFederationService.LdapDirectoryUser("existing-ldap-user", "existing-ldap@example.com"),
          "uid=existing-ldap-user,ou=users,dc=example,dc=com");
    }
    return LdapFederationService.LdapLookupOutcome.notFound();
  }
}
