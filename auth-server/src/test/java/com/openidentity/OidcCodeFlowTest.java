package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;

import com.openidentity.service.OidcGrantService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OidcCodeFlowTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void authorization_code_pkce_refresh_and_revoke_flow() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "oidc", "displayName", "OIDC"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "clientId", "spa-client",
            "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", List.of("https://app.example.com/callback"),
            "grantTypes", List.of("authorization_code", "refresh_token")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "carol", "email", "carol@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    String codeVerifier = "pkce-verifier-1234567890";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response authorizeResp = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "spa-client")
        .formParam("redirect_uri", "https://app.example.com/callback")
        .formParam("scope", "openid profile")
        .formParam("state", "state-123")
        .formParam("code_challenge", codeChallenge)
        .formParam("code_challenge_method", "S256")
        .formParam("username", "carol")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/oidc/protocol/openid-connect/auth")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    Map<String, String> authorizeParams = queryParams(authorizeResp.getHeader("Location"));
    String code = authorizeParams.get("code");

    Response tokenResp = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "spa-client")
        .formParam("code", code)
        .formParam("redirect_uri", "https://app.example.com/callback")
        .formParam("code_verifier", codeVerifier)
        .when().post("/auth/realms/oidc/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(isEmptyOrNullString()))
        .body("id_token", not(isEmptyOrNullString()))
        .body("refresh_token", not(isEmptyOrNullString()))
        .extract().response();

    String accessToken = tokenResp.jsonPath().getString("access_token");
    String refreshToken = tokenResp.jsonPath().getString("refresh_token");

    given()
        .when().get("/auth/realms/oidc/protocol/openid-connect/certs")
        .then().statusCode(200)
        .body("keys[0].kty", is("RSA"))
        .body("keys[0].n", notNullValue())
        .body("keys[0].e", notNullValue());

    given()
        .header("Authorization", "Bearer " + accessToken)
        .when().get("/auth/realms/oidc/protocol/openid-connect/userinfo")
        .then().statusCode(200);

    Response refreshResp = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "refresh_token")
        .formParam("client_id", "spa-client")
        .formParam("refresh_token", refreshToken)
        .when().post("/auth/realms/oidc/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(isEmptyOrNullString()))
        .body("refresh_token", not(isEmptyOrNullString()))
        .extract().response();

    String rotatedRefreshToken = refreshResp.jsonPath().getString("refresh_token");

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("client_id", "spa-client")
        .formParam("token", rotatedRefreshToken)
        .when().post("/auth/realms/oidc/protocol/openid-connect/revoke")
        .then().statusCode(200);

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "refresh_token")
        .formParam("client_id", "spa-client")
        .formParam("refresh_token", rotatedRefreshToken)
        .when().post("/auth/realms/oidc/protocol/openid-connect/token")
        .then().statusCode(400);
  }

  @Test
  void authorization_code_flow_rejects_bad_redirect_and_verifier() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "oidc-errors", "displayName", "OIDC Errors"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "clientId", "web-client",
            "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", List.of("https://client.example.com/callback"),
            "grantTypes", List.of("authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "dave", "email", "dave@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "web-client")
        .formParam("redirect_uri", "https://evil.example.com/callback")
        .formParam("code_challenge", OidcGrantService.codeChallenge("expected-verifier"))
        .formParam("code_challenge_method", "S256")
        .formParam("username", "dave")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/oidc-errors/protocol/openid-connect/auth")
        .then().statusCode(400);

    Response authorizeResp = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "web-client")
        .formParam("redirect_uri", "https://client.example.com/callback")
        .formParam("code_challenge", OidcGrantService.codeChallenge("expected-verifier"))
        .formParam("code_challenge_method", "S256")
        .formParam("username", "dave")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/oidc-errors/protocol/openid-connect/auth")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String code = queryParams(authorizeResp.getHeader("Location")).get("code");

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "web-client")
        .formParam("code", code)
        .formParam("redirect_uri", "https://client.example.com/callback")
        .formParam("code_verifier", "wrong-verifier")
        .when().post("/auth/realms/oidc-errors/protocol/openid-connect/token")
        .then().statusCode(400);
  }

  private Map<String, String> queryParams(String location) {
    Map<String, String> params = new HashMap<>();
    String query = URI.create(location).getQuery();
    if (query == null || query.isBlank()) {
      return params;
    }
    for (String part : query.split("&")) {
      String[] tokens = part.split("=", 2);
      params.put(tokens[0], tokens.length > 1 ? tokens[1] : "");
    }
    return params;
  }
}
