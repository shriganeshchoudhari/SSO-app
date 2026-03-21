package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
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
public class ObservabilityTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void health_and_metrics_endpoints_expose_runtime_state_and_app_counters() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ops", "displayName", "Ops"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "ops-user", "email", "ops-user@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "ops-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/ops-callback")),
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

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "ops-web")
        .formParam("username", "ops-user")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/ops/protocol/openid-connect/token")
        .then().statusCode(200);

    given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "ops-web")
        .queryParam("redirect_uri", "http://client.example/ops-callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "ops-state")
        .queryParam("code_challenge", "ops-code-challenge")
        .queryParam("code_challenge_method", "plain")
        .when().get("/auth/realms/ops/broker/oidc/google/login")
        .then().statusCode(anyOf(is(302), is(303)));

    given()
        .when().get("/q/health/live")
        .then().statusCode(200)
        .body(containsString("\"status\": \"UP\""))
        .body(containsString("\"name\": \"application-liveness\""));

    given()
        .when().get("/q/health/ready")
        .then().statusCode(200)
        .body(containsString("\"status\": \"UP\""))
        .body(containsString("\"name\": \"jwt-signing\""))
        .body(containsString("\"name\": \"secret-protection\""));

    given()
        .when().get("/q/metrics")
        .then().statusCode(200)
        .body(containsString("openidentity_token_grant_total"))
        .body(containsString("grant_type=\"password\""))
        .body(containsString("auth_source=\"local\""))
        .body(containsString("openidentity_broker_flow_total"))
        .body(containsString("protocol=\"oidc\""))
        .body(containsString("step=\"login\""));
  }
}
