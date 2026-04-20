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

  @Test
  void authorization_code_flow_honors_client_consent_and_account_revocation() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "oidc-consent", "displayName", "OIDC Consent"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "clientId", "consent-web",
            "protocol", "openid-connect",
            "publicClient", true,
            "consentRequired", true,
            "redirectUris", List.of("https://consent.example.com/callback"),
            "grantTypes", List.of("authorization_code", "refresh_token")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "erin", "email", "erin@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Consent123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    String codeVerifier = "consent-verifier-1234567890";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response firstAuthorize = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "consent-web")
        .formParam("redirect_uri", "https://consent.example.com/callback")
        .formParam("scope", "openid profile")
        .formParam("state", "consent-state-1")
        .formParam("code_challenge", codeChallenge)
        .formParam("code_challenge_method", "S256")
        .formParam("username", "erin")
        .formParam("password", "Consent123!")
        .when().post("/auth/realms/oidc-consent/protocol/openid-connect/auth")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String consentLocation = firstAuthorize.getHeader("Location");
    if (consentLocation == null || !consentLocation.contains("/auth/realms/oidc-consent/protocol/openid-connect/auth/consent")) {
      throw new AssertionError("expected consent redirect, got: " + consentLocation);
    }
    String consentState = queryParams(consentLocation).get("consent_state");
    if (consentState == null || consentState.isBlank()) {
      throw new AssertionError("missing consent_state");
    }

    String consentPage = given()
        .when().get(consentLocation)
        .then().statusCode(200)
        .extract().asString();
    if (!consentPage.contains("Requested scopes") || !consentPage.contains("openid") || !consentPage.contains("profile")) {
      throw new AssertionError("consent page did not render the requested scopes");
    }

    Response approved = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("consent_state", consentState)
        .formParam("decision", "approve")
        .when().post("/auth/realms/oidc-consent/protocol/openid-connect/auth/consent")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    Map<String, String> approvedParams = queryParams(approved.getHeader("Location"));
    String code = approvedParams.get("code");
    if (code == null || !"consent-state-1".equals(approvedParams.get("state"))) {
      throw new AssertionError("approval did not return the client code/state");
    }

    Response tokenResp = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "consent-web")
        .formParam("code", code)
        .formParam("redirect_uri", "https://consent.example.com/callback")
        .formParam("code_verifier", codeVerifier)
        .when().post("/auth/realms/oidc-consent/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().response();
    String accessToken = tokenResp.jsonPath().getString("access_token");

    Response consentsResp = given()
        .header("Authorization", "Bearer " + accessToken)
        .when().get("/account/consents")
        .then().statusCode(200)
        .extract().response();
    if (!"consent-web".equals(consentsResp.jsonPath().getString("[0].clientId"))) {
      throw new AssertionError("account consent listing did not return the client");
    }
    List<String> scopes = consentsResp.jsonPath().getList("[0].scopes");
    if (scopes == null || !scopes.containsAll(List.of("openid", "profile"))) {
      throw new AssertionError("account consent listing did not return the granted scopes");
    }
    String storedConsentId = consentsResp.jsonPath().getString("[0].id");

    Response repeatAuthorize = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "consent-web")
        .formParam("redirect_uri", "https://consent.example.com/callback")
        .formParam("scope", "openid profile")
        .formParam("state", "consent-state-2")
        .formParam("code_challenge", codeChallenge)
        .formParam("code_challenge_method", "S256")
        .formParam("username", "erin")
        .formParam("password", "Consent123!")
        .when().post("/auth/realms/oidc-consent/protocol/openid-connect/auth")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();
    String repeatLocation = repeatAuthorize.getHeader("Location");
    if (repeatLocation == null || repeatLocation.contains("/auth/realms/oidc-consent/protocol/openid-connect/auth/consent")) {
      throw new AssertionError("repeat authorization should skip consent once it is granted");
    }

    given()
        .header("Authorization", "Bearer " + accessToken)
        .when().delete("/account/consents/" + storedConsentId)
        .then().statusCode(204);

    Response afterRevoke = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "consent-web")
        .formParam("redirect_uri", "https://consent.example.com/callback")
        .formParam("scope", "openid profile")
        .formParam("state", "consent-state-3")
        .formParam("code_challenge", codeChallenge)
        .formParam("code_challenge_method", "S256")
        .formParam("username", "erin")
        .formParam("password", "Consent123!")
        .when().post("/auth/realms/oidc-consent/protocol/openid-connect/auth")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();
    String afterRevokeLocation = afterRevoke.getHeader("Location");
    if (afterRevokeLocation == null || !afterRevokeLocation.contains("/auth/realms/oidc-consent/protocol/openid-connect/auth/consent")) {
      throw new AssertionError("authorization after revocation should require consent again");
    }

    String revokedConsentState = queryParams(afterRevokeLocation).get("consent_state");
    Response denied = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("consent_state", revokedConsentState)
        .formParam("decision", "deny")
        .when().post("/auth/realms/oidc-consent/protocol/openid-connect/auth/consent")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();
    Map<String, String> deniedParams = queryParams(denied.getHeader("Location"));
    if (!"access_denied".equals(deniedParams.get("error")) || !"consent-state-3".equals(deniedParams.get("state"))) {
      throw new AssertionError("denied consent did not redirect back with access_denied");
    }
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
