package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AuthFlowTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void realm_user_password_token_session_logout_flow() {
    // Create realm
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "demo", "displayName", "Demo"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");
    if (realmId == null || realmId.isBlank()) {
      // try location header segment
      String loc = realmResp.getHeader("Location");
      realmId = loc != null ? loc.substring(loc.lastIndexOf('/')+1) : null;
    }

    // Create user
    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "alice", "email", "alice@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    // Set password
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    // Get token using password grant
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "alice")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/demo/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(isEmptyOrNullString()))
        .body("id_token", not(isEmptyOrNullString()))
        .body("token_type", equalToIgnoringCase("Bearer"))
        .body("expires_in", greaterThan(0));

    // List sessions
    Response sessionsResp = adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then().statusCode(200)
        .extract().response();
    String sid = sessionsResp.jsonPath().getString("[0].id");

    // Logout via protocol (sid)
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("sid", sid)
        .when().post("/auth/realms/demo/protocol/openid-connect/logout")
        .then().statusCode(anyOf(is(200), is(204)));
  }

  @Test
  void userinfo_and_introspection_reject_tampered_tokens() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "security", "displayName", "Security"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "mallory", "email", "mallory@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    String accessToken = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "mallory")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/security/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().jsonPath().getString("access_token");

    String tampered = accessToken.substring(0, accessToken.length() - 1) + "x";

    given()
        .header("Authorization", "Bearer " + tampered)
        .when().get("/auth/realms/security/protocol/openid-connect/userinfo")
        .then().statusCode(401);

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("token", tampered)
        .when().post("/auth/realms/security/protocol/openid-connect/token/introspect")
        .then().statusCode(200)
        .body("active", is(false));
  }
}
