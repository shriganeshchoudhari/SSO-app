package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RealmConfigExportImportTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  private String createRealm(String name, String displayName) {
    return adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "name", name,
                "displayName", displayName,
                "enabled", true,
                "mfaPolicy", "required"))
        .when()
        .post("/admin/realms")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .extract()
        .jsonPath()
        .getString("id");
  }

  @Test
  @SuppressWarnings("unchecked")
  void export_import_realm_config_round_trips_without_leaking_secrets() {
    String sourceRealmId = createRealm("cfg-source", "Config Source");

    Response browserClient =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "clientId", "browser-app",
                    "protocol", "openid-connect",
                    "publicClient", true,
                    "consentRequired", true,
                    "redirectUris", List.of("https://browser.example.test/callback"),
                    "grantTypes", List.of("authorization_code", "refresh_token")))
            .when()
            .post("/admin/realms/" + sourceRealmId + "/clients")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .response();
    String browserClientId = browserClient.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "clientId", "service-app",
                "protocol", "openid-connect",
                "secret", "service-secret",
                "publicClient", false,
                "redirectUris", List.of(),
                "grantTypes", List.of("client_credentials")))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/clients")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "realm-reader"))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/roles")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "browser-user", "clientId", browserClientId))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/roles")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "name", "acme",
                "displayName", "Acme Corp",
                "logoText", "AC",
                "primaryColor", "#123456",
                "accentColor", "#654321",
                "locale", "en-US",
                "enabled", true))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/organizations")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.ofEntries(
                Map.entry("name", "corp-ldap"),
                Map.entry("url", "ldap://ldap.example.test:389"),
                Map.entry("bindDn", "cn=admin,dc=example,dc=test"),
                Map.entry("bindCredential", "bind-secret"),
                Map.entry("userSearchBase", "ou=users,dc=example,dc=test"),
                Map.entry("userSearchFilter", "(uid={username})"),
                Map.entry("usernameAttribute", "uid"),
                Map.entry("emailAttribute", "mail"),
                Map.entry("syncAttributesOnLogin", true),
                Map.entry("disableMissingUsers", true),
                Map.entry("enabled", true)))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/federation/ldap")
        .then()
        .statusCode(anyOf(is(200), is(201)));

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
                Map.entry("usernameClaim", "preferred_username"),
                Map.entry("emailClaim", "email"),
                Map.entry("syncAttributesOnLogin", true),
                Map.entry("enabled", true)))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/brokering/oidc")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "alias", "saml-idp",
                "entityId", "urn:test:saml:idp",
                "ssoUrl", "http://saml.example.test/sso",
                "sloUrl", "http://saml.example.test/slo",
                "x509Certificate", "-----BEGIN CERTIFICATE-----TEST-----END CERTIFICATE-----",
                "nameIdFormat", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
                "syncAttributesOnLogin", true,
                "wantAuthnRequestsSigned", true,
                "enabled", true))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/brokering/saml")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("deprovisionMode", "delete"))
        .when()
        .put("/admin/realms/" + sourceRealmId + "/scim/settings")
        .then()
        .statusCode(200);

    adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "name", "outbound-main",
                "baseUrl", "http://scim-target.example.test/scim/v2",
                "bearerToken", "outbound-secret",
                "enabled", true,
                "syncOnUserChange", true,
                "syncOnGroupChange", true,
                "deleteOnLocalDelete", true,
                "deleteGroupOnLocalDelete", true))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/scim/outbound-targets")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "name", "ops-webhook",
                "url", "https://hooks.example.test/openidentity",
                "signingSecret", "webhook-secret",
                "subscribedEvents", List.of("login.*", "admin.*"),
                "enabled", true))
        .when()
        .post("/admin/realms/" + sourceRealmId + "/webhooks")
        .then()
        .statusCode(anyOf(is(200), is(201)));

    Map<String, Object> exported =
        adminRequest()
            .when()
            .get("/admin/realms/" + sourceRealmId + "/config/export")
            .then()
            .statusCode(200)
            .body("schemaVersion", equalTo("v1"))
            .body("realm.name", equalTo("cfg-source"))
            .body("realm.mfaPolicy", equalTo("required"))
            .body("clients", hasSize(2))
            .body("roles", hasSize(2))
            .body("organizations", hasSize(1))
            .body("ldapProviders", hasSize(1))
            .body("oidcIdentityProviders", hasSize(1))
            .body("samlIdentityProviders", hasSize(1))
            .body("scimOutboundTargets", hasSize(1))
            .body("webhooks", hasSize(1))
            .extract()
            .as(Map.class);

    List<Map<String, Object>> exportedClients =
        (List<Map<String, Object>>) exported.get("clients");
    Map<String, Object> exportedServiceClient =
        exportedClients.stream()
            .filter(client -> "service-app".equals(client.get("clientId")))
            .findFirst()
            .orElseThrow();
    Assertions.assertNull(exportedServiceClient.get("secret"));
    Assertions.assertEquals(Boolean.TRUE, exportedServiceClient.get("hasSecret"));

    List<Map<String, Object>> exportedLdapProviders =
        (List<Map<String, Object>>) exported.get("ldapProviders");
    Assertions.assertNull(exportedLdapProviders.getFirst().get("bindCredential"));
    Assertions.assertEquals(Boolean.TRUE, exportedLdapProviders.getFirst().get("bindCredentialConfigured"));

    List<Map<String, Object>> exportedOidcProviders =
        (List<Map<String, Object>>) exported.get("oidcIdentityProviders");
    Assertions.assertNull(exportedOidcProviders.getFirst().get("clientSecret"));
    Assertions.assertEquals(Boolean.TRUE, exportedOidcProviders.getFirst().get("clientSecretConfigured"));

    List<Map<String, Object>> exportedTargets =
        (List<Map<String, Object>>) exported.get("scimOutboundTargets");
    Assertions.assertNull(exportedTargets.getFirst().get("bearerToken"));
    Assertions.assertEquals(Boolean.TRUE, exportedTargets.getFirst().get("bearerTokenConfigured"));

    List<Map<String, Object>> exportedWebhooks =
        (List<Map<String, Object>>) exported.get("webhooks");
    Assertions.assertNull(exportedWebhooks.getFirst().get("signingSecret"));
    Assertions.assertEquals(Boolean.TRUE, exportedWebhooks.getFirst().get("signingSecretConfigured"));

    exportedServiceClient.put("secret", "service-secret");
    exportedLdapProviders.getFirst().put("bindCredential", "bind-secret");
    exportedOidcProviders.getFirst().put("clientSecret", "dex-secret");
    exportedTargets.getFirst().put("bearerToken", "outbound-secret");
    exportedWebhooks.getFirst().put("signingSecret", "webhook-secret");

    String targetRealmId = createRealm("cfg-target", "Config Target");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(exported)
        .when()
        .post("/admin/realms/" + targetRealmId + "/config/import")
        .then()
        .statusCode(200)
        .body("realmId", equalTo(targetRealmId))
        .body("realmUpdated", equalTo(true))
        .body("scimSettingsUpdated", equalTo(true))
        .body("clients.created", equalTo(2))
        .body("roles.created", equalTo(2))
        .body("organizations.created", equalTo(1))
        .body("ldapProviders.created", equalTo(1))
        .body("oidcIdentityProviders.created", equalTo(1))
        .body("samlIdentityProviders.created", equalTo(1))
        .body("scimOutboundTargets.created", equalTo(1))
        .body("webhooks.created", equalTo(1));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(exported)
        .when()
        .post("/admin/realms/" + targetRealmId + "/config/import")
        .then()
        .statusCode(200)
        .body("clients.created", equalTo(0))
        .body("roles.created", equalTo(0))
        .body("organizations.created", equalTo(0))
        .body("ldapProviders.created", equalTo(0))
        .body("oidcIdentityProviders.created", equalTo(0))
        .body("samlIdentityProviders.created", equalTo(0))
        .body("scimOutboundTargets.created", equalTo(0))
        .body("webhooks.created", equalTo(0));

    adminRequest()
        .when()
        .get("/admin/realms/" + targetRealmId + "/config/export")
        .then()
        .statusCode(200)
        .body("realm.name", equalTo("cfg-target"))
        .body("realm.displayName", equalTo("Config Source"))
        .body("realm.mfaPolicy", equalTo("required"))
        .body("clients", hasSize(2))
        .body("roles", hasSize(2))
        .body("organizations[0].name", equalTo("acme"))
        .body("ldapProviders[0].bindCredentialConfigured", equalTo(true))
        .body("oidcIdentityProviders[0].clientSecretConfigured", equalTo(true))
        .body("samlIdentityProviders[0].alias", equalTo("saml-idp"))
        .body("scimSettings.deprovisionMode", equalTo("delete"))
        .body("scimOutboundTargets[0].bearerTokenConfigured", equalTo(true))
        .body("webhooks[0].signingSecretConfigured", equalTo(true));
  }
}
