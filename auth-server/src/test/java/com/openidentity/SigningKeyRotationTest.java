package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for JWT signing key persistence, rotation, and JWKS exposure.
 */
@QuarkusTest
public class SigningKeyRotationTest {

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void list_signing_keys_returns_active_key_on_startup() {
    adminRequest()
        .when().get("/admin/keys")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].status", equalTo("active"))
        .body("[0].kid", notNullValue())
        .body("[0].algorithm", equalTo("RS256"));
  }

  @Test
  void rotate_creates_new_active_key_and_retires_old() {
    // Get current active kid
    Response listBefore = adminRequest()
        .when().get("/admin/keys")
        .then().statusCode(200).extract().response();

    String kidBefore = listBefore.jsonPath().getString("[0].kid");

    // Rotate
    Response rotateResp = adminRequest()
        .contentType(ContentType.JSON)
        .when().post("/admin/keys/rotate")
        .then()
        .statusCode(200)
        .body("status", equalTo("active"))
        .body("kid", notNullValue())
        .extract().response();

    String newKid = rotateResp.jsonPath().getString("kid");

    // New kid must differ from old
    org.junit.jupiter.api.Assertions.assertNotEquals(kidBefore, newKid);

    // List now has at least 2 keys: one active and one retired
    Response listAfter = adminRequest()
        .when().get("/admin/keys")
        .then().statusCode(200).extract().response();

    java.util.List<String> statuses = listAfter.jsonPath().getList("status");
    org.junit.jupiter.api.Assertions.assertTrue(
        statuses.contains("active"), "Expected at least one active key");
    org.junit.jupiter.api.Assertions.assertTrue(
        statuses.contains("retired"), "Expected retired key after rotation");
  }

  @Test
  void jwks_endpoint_serves_at_least_one_key() {
    // First create a realm so JWKS path resolves
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "keytest", "displayName", "Key Test"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .when().get("/auth/realms/keytest/protocol/openid-connect/certs")
        .then()
        .statusCode(200)
        .body("keys", not(empty()))
        .body("keys[0].kty", equalTo("RSA"))
        .body("keys[0].use", equalTo("sig"))
        .body("keys[0].alg", equalTo("RS256"))
        .body("keys[0].kid", notNullValue())
        .body("keys[0].n", notNullValue())
        .body("keys[0].e", notNullValue());
  }

  @Test
  void per_realm_discovery_contains_resolved_endpoints() {
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "discoverytest", "displayName", "Discovery Test"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .when().get("/auth/realms/discoverytest/.well-known/openid-configuration")
        .then()
        .statusCode(200)
        .body("issuer", notNullValue())
        .body("authorization_endpoint", containsString("/discoverytest/"))
        .body("token_endpoint", containsString("/discoverytest/"))
        .body("jwks_uri", containsString("/discoverytest/"))
        // Must NOT contain literal {realm}
        .body("authorization_endpoint", not(containsString("{realm}")))
        .body("token_endpoint", not(containsString("{realm}")))
        .body("jwks_uri", not(containsString("{realm}")));
  }

  @Test
  void health_check_reports_key_age() {
    given()
        .when().get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("checks.find { it.name == 'jwt-signing' }.status", equalTo("UP"))
        .body("checks.find { it.name == 'jwt-signing' }.data.keyAgeHours", notNullValue());
  }
}
