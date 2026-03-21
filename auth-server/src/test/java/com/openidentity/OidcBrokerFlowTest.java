package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.openidentity.service.OidcGrantService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OidcBrokerFlowTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void oidc_broker_login_returns_local_authorization_code() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "broker-live", "displayName", "Broker Live"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "broker-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code", "refresh_token"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
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
            new AbstractMap.SimpleEntry<>("usernameClaim", "preferred_username"),
            new AbstractMap.SimpleEntry<>("emailClaim", "email"),
            new AbstractMap.SimpleEntry<>("syncAttributesOnLogin", true),
            new AbstractMap.SimpleEntry<>("enabled", true)))
        .when().post("/admin/realms/" + realmId + "/brokering/oidc")
        .then().statusCode(anyOf(is(200), is(201)));

    String codeVerifier = "broker-code-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "broker-web")
        .queryParam("redirect_uri", "http://client.example/callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "app-state-123")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/broker-live/broker/oidc/google/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI externalRedirect = URI.create(brokerStart.getHeader("Location"));
    String brokerState = queryValue(externalRedirect, "state");
    if (brokerState == null) {
      throw new AssertionError("broker state missing from provider redirect");
    }

    Response callbackResponse = given()
        .redirects().follow(false)
        .queryParam("code", "mock-google-code")
        .queryParam("state", brokerState)
        .when().get("/auth/realms/broker-live/broker/oidc/google/callback")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI clientRedirect = URI.create(callbackResponse.getHeader("Location"));
    String localCode = queryValue(clientRedirect, "code");
    String returnedState = queryValue(clientRedirect, "state");
    if (localCode == null) {
      throw new AssertionError("local authorization code missing from client redirect");
    }

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "broker-web")
        .formParam("code", localCode)
        .formParam("redirect_uri", "http://client.example/callback")
        .formParam("code_verifier", codeVerifier)
        .when().post("/auth/realms/broker-live/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", notNullValue())
        .body("id_token", notNullValue());

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("username", hasItem("brokered-user"))
        .body("email", hasItem("brokered-user@example.com"))
        .body("federationSource", hasItem("oidc"));

    if (!"app-state-123".equals(returnedState)) {
      throw new AssertionError("original client state was not preserved");
    }
  }

  @Test
  void oidc_managed_users_keep_local_profile_when_sync_disabled_and_reject_local_mutations() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "broker-policy", "displayName", "Broker Policy"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "broker-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code", "refresh_token"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "brokered-user", "email", "keepme@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
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
            new AbstractMap.SimpleEntry<>("usernameClaim", "preferred_username"),
            new AbstractMap.SimpleEntry<>("emailClaim", "email"),
            new AbstractMap.SimpleEntry<>("syncAttributesOnLogin", false),
            new AbstractMap.SimpleEntry<>("enabled", true)))
        .when().post("/admin/realms/" + realmId + "/brokering/oidc")
        .then().statusCode(anyOf(is(200), is(201)));

    String codeVerifier = "broker-policy-code-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "broker-web")
        .queryParam("redirect_uri", "http://client.example/callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "app-state-policy")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/broker-policy/broker/oidc/google/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String brokerState = queryValue(URI.create(brokerStart.getHeader("Location")), "state");
    Response callbackResponse = given()
        .redirects().follow(false)
        .queryParam("code", "mock-google-code-updated")
        .queryParam("state", brokerState)
        .when().get("/auth/realms/broker-policy/broker/oidc/google/callback")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String localCode = queryValue(URI.create(callbackResponse.getHeader("Location")), "code");
    String accessToken = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "broker-web")
        .formParam("code", localCode)
        .formParam("redirect_uri", "http://client.example/callback")
        .formParam("code_verifier", codeVerifier)
        .when().post("/auth/realms/broker-policy/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().jsonPath().getString("access_token");

    Response usersResponse = adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].email", equalTo("keepme@example.com"))
        .body("[0].federationSource", equalTo("oidc"))
        .extract().response();
    String userId = usersResponse.jsonPath().getString("[0].id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "LocalPassword123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(409);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .contentType(ContentType.JSON)
        .body(Map.of("password", "AnotherLocalSecret123!"))
        .when().post("/account/credentials/password")
        .then().statusCode(409);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .contentType(ContentType.JSON)
        .body(Map.of("email", "change-me@example.com"))
        .when().put("/account/profile")
        .then().statusCode(409);

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "admin-change@example.com", "enabled", true))
        .when().put("/admin/realms/" + realmId + "/users/" + userId)
        .then().statusCode(409);

    Response resetResponse = given()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "keepme@example.com"))
        .when().post("/auth/realms/broker-policy/password-reset/request")
        .then().statusCode(anyOf(is(200), is(204)))
        .extract().response();

    String resetBody = resetResponse.getBody().asString();
    if (resetBody != null && !resetBody.isBlank()) {
      String resetToken = resetResponse.jsonPath().getString("token");
      if (resetToken != null) {
        throw new AssertionError("OIDC-managed users must not receive password reset tokens");
      }
    }
  }

  @Test
  void oidc_managed_user_can_be_detached_to_local_account() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "broker-detach", "displayName", "Broker Detach"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "broker-detach-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/detach-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code", "password"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
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
            new AbstractMap.SimpleEntry<>("usernameClaim", "preferred_username"),
            new AbstractMap.SimpleEntry<>("emailClaim", "email"),
            new AbstractMap.SimpleEntry<>("syncAttributesOnLogin", true),
            new AbstractMap.SimpleEntry<>("enabled", true)))
        .when().post("/admin/realms/" + realmId + "/brokering/oidc")
        .then().statusCode(anyOf(is(200), is(201)));

    String codeVerifier = "broker-detach-code-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);
    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "broker-detach-web")
        .queryParam("redirect_uri", "http://client.example/detach-callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "detach-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/broker-detach/broker/oidc/google/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();
    String brokerState = queryValue(URI.create(brokerStart.getHeader("Location")), "state");

    given()
        .redirects().follow(false)
        .queryParam("code", "mock-google-code")
        .queryParam("state", brokerState)
        .when().get("/auth/realms/broker-detach/broker/oidc/google/callback")
        .then().statusCode(anyOf(is(302), is(303)));

    Response usersResponse = adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].federationSource", equalTo("oidc"))
        .extract().response();
    String userId = usersResponse.jsonPath().getString("[0].id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "DetachLocal123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/detach-federation")
        .then().statusCode(200)
        .body("federationSource", equalTo(null));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "local-detached@example.com", "enabled", true))
        .when().put("/admin/realms/" + realmId + "/users/" + userId)
        .then().statusCode(anyOf(is(200), is(204)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "broker-detach-web")
        .formParam("username", "brokered-user")
        .formParam("password", "DetachLocal123!")
        .when().post("/auth/realms/broker-detach/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", notNullValue());
  }

  private String queryValue(URI uri, String key) {
    if (uri.getQuery() == null) {
      return null;
    }
    for (String part : uri.getQuery().split("&")) {
      if (part.startsWith(key + "=")) {
        return java.net.URLDecoder.decode(part.substring(key.length() + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }
}
