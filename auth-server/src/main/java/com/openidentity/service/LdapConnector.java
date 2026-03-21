package com.openidentity.service;

import com.openidentity.domain.LdapProviderEntity;
import java.util.Optional;

public interface LdapConnector {
  LdapFederationService.LdapAuthenticationOutcome authenticate(
      LdapProviderEntity provider,
      String username,
      String password,
      String bindCredential);

  LdapFederationService.LdapLookupOutcome lookupUser(
      LdapProviderEntity provider,
      String username,
      String bindCredential);
}
