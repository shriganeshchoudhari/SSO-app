package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PasswordResetAndEmailVerificationTest {

  @Test
  void password_reset_and_email_verify_flow() {
    // Create realm
    Response realmResp = given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "demo2", "displayName", "Demo2"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");
    if (realmId == null || realmId.isBlank()) {
      String loc = realmResp.getHeader("Location");
      realmId = loc != null ? loc.substring(loc.lastIndexOf('/') + 1) : null;
    }

    // Create user with email
    Response userResp = given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "bob", "email", "bob@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    // Request email verification token (dev returns token in test)
    String verifyToken = given()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "bob@example.com"))
        .when().post("/auth/realms/demo2/email/verify/request")
        .then().statusCode(anyOf(is(200), is(204)))
        .body("token", anyOf(nullValue(), not(isEmptyOrNullString())))
        .extract().jsonPath().getString("token");
    if (verifyToken != null) {
      given()
          .contentType(ContentType.JSON)
          .body(Map.of("token", verifyToken))
          .when().post("/auth/realms/demo2/email/verify/confirm")
          .then().statusCode(anyOf(is(200), is(204)));
    }

    // Request password reset token
    String resetToken = given()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "bob@example.com"))
        .when().post("/auth/realms/demo2/password-reset/request")
        .then().statusCode(anyOf(is(200), is(204)))
        .body("token", anyOf(nullValue(), not(isEmptyOrNullString())))
        .extract().jsonPath().getString("token");
    if (resetToken != null) {
      given()
          .contentType(ContentType.JSON)
          .body(Map.of("token", resetToken, "newPassword", "NewSecret123!"))
          .when().post("/auth/realms/demo2/password-reset/confirm")
          .then().statusCode(anyOf(is(200), is(204)));
    }

    // Ensure we can login with new password
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "bob")
        .formParam("password", "NewSecret123!")
        .when().post("/auth/realms/demo2/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(isEmptyOrNullString()));
  }
}

