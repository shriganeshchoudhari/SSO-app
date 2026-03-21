package com.openidentity.service;

import com.openidentity.domain.OidcIdentityProviderEntity;
import java.net.URI;

public interface OidcBrokerConnector {
  record BrokerProfile(String subject, String username, String email, Boolean emailVerified) {}

  BrokerProfile exchangeAuthorizationCode(
      OidcIdentityProviderEntity provider,
      URI callbackUri,
      String authorizationCode,
      String clientSecret);
}
