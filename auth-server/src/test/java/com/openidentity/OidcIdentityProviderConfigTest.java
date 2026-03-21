package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OidcIdentityProviderConfigTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void oidc_identity_provider_crud_flow() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "broker-config", "displayName", "Broker Config"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response providerResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("alias", "google"),
            new AbstractMap.SimpleEntry<>("issuerUrl", "https://accounts.google.com"),
            new AbstractMap.SimpleEntry<>("authorizationUrl", "https://accounts.google.com/o/oauth2/v2/auth"),
            new AbstractMap.SimpleEntry<>("tokenUrl", "https://oauth2.googleapis.com/token"),
            new AbstractMap.SimpleEntry<>("userInfoUrl", "https://openidconnect.googleapis.com/v1/userinfo"),
            new AbstractMap.SimpleEntry<>("jwksUrl", "https://www.googleapis.com/oauth2/v3/certs"),
            new AbstractMap.SimpleEntry<>("clientId", "google-client"),
            new AbstractMap.SimpleEntry<>("clientSecret", "GoogleClientSecret123!"),
            new AbstractMap.SimpleEntry<>("scopes", List.of("openid", "profile", "email")),
            new AbstractMap.SimpleEntry<>("usernameClaim", "email"),
            new AbstractMap.SimpleEntry<>("emailClaim", "email"),
            new AbstractMap.SimpleEntry<>("syncAttributesOnLogin", true),
            new AbstractMap.SimpleEntry<>("enabled", true)))
        .when().post("/admin/realms/" + realmId + "/brokering/oidc")
        .then().statusCode(anyOf(is(200), is(201)))
        .body("alias", equalTo("google"))
        .body("clientSecretConfigured", equalTo(true))
        .body("scopes", hasItems("openid", "profile", "email"))
        .extract().response();
    String providerId = providerResp.jsonPath().getString("id");

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/brokering/oidc")
        .then().statusCode(200)
        .body("[0].alias", equalTo("google"))
        .body("[0].clientId", equalTo("google-client"))
        .body("[0].enabled", equalTo(true));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "enabled", false,
            "usernameClaim", "preferred_username",
            "syncAttributesOnLogin", false))
        .when().put("/admin/realms/" + realmId + "/brokering/oidc/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/brokering/oidc/" + providerId)
        .then().statusCode(200)
        .body("enabled", equalTo(false))
        .body("usernameClaim", equalTo("preferred_username"))
        .body("syncAttributesOnLogin", equalTo(false));

    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/brokering/oidc/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/brokering/oidc")
        .then().statusCode(200)
        .body("size()", equalTo(0));
  }
}
