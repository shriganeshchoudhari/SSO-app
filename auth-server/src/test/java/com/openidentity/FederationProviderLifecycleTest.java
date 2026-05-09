package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FederationProviderLifecycleTest {
  @Inject EntityManager em;

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  private String createRealm(String name) {
    return adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name, "displayName", name))
        .when()
        .post("/admin/realms")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .extract()
        .jsonPath()
        .getString("id");
  }

  String createFederatedUser(String realmId, String username, String source, String providerId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              RealmEntity realm = em.find(RealmEntity.class, UUID.fromString(realmId));
              UserEntity user = new UserEntity();
              user.setId(UUID.randomUUID());
              user.setRealm(realm);
              user.setUsername(username);
              user.setEmail(username + "@example.com");
              user.setEnabled(Boolean.TRUE);
              user.setEmailVerified(Boolean.TRUE);
              user.setFederationSource(source);
              user.setFederationProviderId(UUID.fromString(providerId));
              user.setFederationExternalId(source + "-" + username);
              user.setCreatedAt(OffsetDateTime.now());
              em.persist(user);
              return user.getId().toString();
            });
  }

  @Test
  void ldap_provider_delete_blocks_then_can_disable_local_users() {
    String realmId = createRealm("ldap-provider-lifecycle");

    String providerId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name", "corp-ldap",
                    "url", "ldap://ldap.example.test:389",
                    "bindDn", "cn=admin,dc=example,dc=test",
                    "bindCredential", "bind-secret",
                    "enabled", true))
            .when()
            .post("/admin/realms/" + realmId + "/federation/ldap")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .jsonPath()
            .getString("id");

    String userId = createFederatedUser(realmId, "ldap-user", "ldap", providerId);

    adminRequest()
        .when()
        .delete("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then()
        .statusCode(409);

    adminRequest()
        .queryParam("linkedUserAction", "disable_local")
        .when()
        .delete("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then()
        .statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(200)
        .body("enabled", equalTo(false))
        .body("federationSource", nullValue())
        .body("federationProviderId", nullValue());
  }

  @Test
  void oidc_provider_delete_can_delete_linked_users() {
    String realmId = createRealm("oidc-provider-lifecycle");

    String providerId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.ofEntries(
                    Map.entry("alias", "dex"),
                    Map.entry("issuerUrl", "http://dex.example.test"),
                    Map.entry("authorizationUrl", "http://dex.example.test/auth"),
                    Map.entry("tokenUrl", "http://dex.example.test/token"),
                    Map.entry("userInfoUrl", "http://dex.example.test/userinfo"),
                    Map.entry("jwksUrl", "http://dex.example.test/jwks"),
                    Map.entry("clientId", "dex-client"),
                    Map.entry("clientSecret", "dex-secret"),
                    Map.entry("scopes", List.of("openid", "profile", "email")),
                    Map.entry("enabled", true)))
            .when()
            .post("/admin/realms/" + realmId + "/brokering/oidc")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .jsonPath()
            .getString("id");

    String userId = createFederatedUser(realmId, "oidc-user", "oidc", providerId);

    adminRequest()
        .queryParam("linkedUserAction", "delete_users")
        .when()
        .delete("/admin/realms/" + realmId + "/brokering/oidc/" + providerId)
        .then()
        .statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(404);

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/brokering/oidc")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }

  @Test
  void saml_provider_delete_can_disable_local_users() {
    String realmId = createRealm("saml-provider-lifecycle");

    String providerId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "alias", "adfs",
                    "entityId", "https://adfs.example.test/entity",
                    "ssoUrl", "https://adfs.example.test/sso",
                    "enabled", true))
            .when()
            .post("/admin/realms/" + realmId + "/brokering/saml")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .jsonPath()
            .getString("id");

    String userId = createFederatedUser(realmId, "saml-user", "saml", providerId);

    adminRequest()
        .queryParam("linkedUserAction", "disable_local")
        .when()
        .delete("/admin/realms/" + realmId + "/brokering/saml/" + providerId)
        .then()
        .statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(200)
        .body("enabled", equalTo(false))
        .body("federationSource", nullValue())
        .body("federationProviderId", nullValue());
  }
}
