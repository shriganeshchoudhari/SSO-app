package com.openidentity.support;

import com.openidentity.domain.OidcIdentityProviderEntity;
import com.openidentity.service.OidcBrokerConnector;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;

@Mock
@ApplicationScoped
public class TestOidcBrokerConnector implements OidcBrokerConnector {
  @Override
  public BrokerProfile exchangeAuthorizationCode(
      OidcIdentityProviderEntity provider,
      URI callbackUri,
      String authorizationCode,
      String clientSecret) {
    if (!Boolean.TRUE.equals(provider.getEnabled())) {
      throw new IllegalStateException("Provider disabled");
    }
    if ("mock-google-code".equals(authorizationCode)) {
      return new BrokerProfile("google-subject-123", "brokered-user", "brokered-user@example.com", Boolean.TRUE);
    }
    if ("mock-google-code-updated".equals(authorizationCode)) {
      return new BrokerProfile("google-subject-123", "brokered-user", "brokered-updated@example.com", Boolean.TRUE);
    }
    throw new IllegalStateException("Unknown broker authorization code");
  }
}
