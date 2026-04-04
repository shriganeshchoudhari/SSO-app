package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.openidentity.support.TestScimOutboundConnector;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ScimOutboundProvisioningTest {

  @BeforeEach
  void resetConnector() {
    TestScimOutboundConnector.reset();
  }

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  private String createRealm(String name) {
    Response r =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "displayName", name))
            .when()
            .post("/admin/realms")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .response();
    return r.jsonPath().getString("id");
  }

  private String createUser(String realmId, String username, String email, boolean enabled) {
    Response r =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "email", email, "enabled", enabled))
            .when()
            .post("/admin/realms/" + realmId + "/users")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .response();
    return r.jsonPath().getString("id");
  }

  private void setPassword(String realmId, String userId, String password) {
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", password))
        .when()
        .post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then()
        .statusCode(anyOf(is(200), is(201), is(204)));
  }

  private String accessToken(String realmName, String username, String password) {
    return given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", username)
        .formParam("password", password)
        .when()
        .post("/auth/realms/" + realmName + "/protocol/openid-connect/token")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("access_token");
  }

  @Test
  void create_list_update_and_sync_outbound_target() {
    String realmId = createRealm("scimoutbound1");
    String activeUserId = createUser(realmId, "outbound-user", "outbound@example.com", true);
    String disabledUserId = createUser(realmId, "disabled-user", "disabled@example.com", false);

    Response createTarget =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name",
                    "hr-suite",
                    "baseUrl",
                    "https://scim.example.test/scim/v2",
                    "bearerToken",
                    "outbound-secret-token",
                    "enabled",
                    true))
            .when()
            .post("/admin/realms/" + realmId + "/scim/outbound-targets")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("id", notNullValue())
            .body("name", equalTo("hr-suite"))
            .body("hasBearerToken", equalTo(true))
            .extract()
            .response();

    String targetId = createTarget.jsonPath().getString("id");

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/scim/outbound-targets")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].name", equalTo("hr-suite"));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("baseUrl", "https://scim.example.test/scim/v2/tenant-a"))
        .when()
        .put("/admin/realms/" + realmId + "/scim/outbound-targets/" + targetId)
        .then()
        .statusCode(200)
        .body("baseUrl", equalTo("https://scim.example.test/scim/v2/tenant-a"));

    adminRequest()
        .when()
        .post("/admin/realms/" + realmId + "/scim/outbound-targets/" + targetId + "/sync-users")
        .then()
        .statusCode(200)
        .body("processedUsers", equalTo(2))
        .body("createdUsers", equalTo(2))
        .body("updatedUsers", equalTo(0))
        .body("lastSyncedAt", notNullValue());

    Map<String, Object> activePayload =
        TestScimOutboundConnector.payload(UUID.fromString(targetId), activeUserId);
    Map<String, Object> disabledPayload =
        TestScimOutboundConnector.payload(UUID.fromString(targetId), disabledUserId);
    org.junit.jupiter.api.Assertions.assertNotNull(activePayload);
    org.junit.jupiter.api.Assertions.assertNotNull(disabledPayload);
    org.junit.jupiter.api.Assertions.assertEquals(activeUserId, activePayload.get("externalId"));
    org.junit.jupiter.api.Assertions.assertEquals("outbound-user", activePayload.get("userName"));
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, activePayload.get("active"));
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, disabledPayload.get("active"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> emails = (List<Map<String, Object>>) activePayload.get("emails");
    org.junit.jupiter.api.Assertions.assertEquals("outbound@example.com", emails.get(0).get("value"));

    adminRequest()
        .when()
        .post("/admin/realms/" + realmId + "/scim/outbound-targets/" + targetId + "/sync-users")
        .then()
        .statusCode(200)
        .body("processedUsers", equalTo(2))
        .body("createdUsers", equalTo(0))
        .body("updatedUsers", equalTo(2));
  }

  @Test
  void disabled_outbound_target_rejects_manual_sync() {
    String realmId = createRealm("scimoutbound2");
    createUser(realmId, "blocked-user", "blocked@example.com", true);

    String targetId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name",
                    "disabled-target",
                    "baseUrl",
                    "https://scim.example.test/scim/v2",
                    "bearerToken",
                    "disabled-secret",
                    "enabled",
                    false))
            .when()
            .post("/admin/realms/" + realmId + "/scim/outbound-targets")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .response()
            .jsonPath()
            .getString("id");

    adminRequest()
        .when()
        .post("/admin/realms/" + realmId + "/scim/outbound-targets/" + targetId + "/sync-users")
        .then()
        .statusCode(409);
  }

  @Test
  void local_user_delete_propagates_remote_scim_delete_when_target_opted_in() {
    String realmId = createRealm("scimoutbound3");
    String userId = createUser(realmId, "remote-delete-user", "remote-delete@example.com", true);

    String targetId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name",
                    "remote-delete-target",
                    "baseUrl",
                    "https://scim.example.test/scim/v2",
                    "bearerToken",
                    "remote-delete-secret",
                    "enabled",
                    true,
                    "deleteOnLocalDelete",
                    true))
            .when()
            .post("/admin/realms/" + realmId + "/scim/outbound-targets")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("deleteOnLocalDelete", equalTo(true))
            .extract()
            .response()
            .jsonPath()
            .getString("id");

    adminRequest()
        .when()
        .post("/admin/realms/" + realmId + "/scim/outbound-targets/" + targetId + "/sync-users")
        .then()
        .statusCode(200)
        .body("processedUsers", equalTo(1));

    org.junit.jupiter.api.Assertions.assertNotNull(
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId));

    adminRequest()
        .when()
        .delete("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(204);

    org.junit.jupiter.api.Assertions.assertTrue(
        TestScimOutboundConnector.wasDeleted(UUID.fromString(targetId), userId));
    org.junit.jupiter.api.Assertions.assertNull(
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId));
  }

  @Test
  void local_user_delete_does_not_propagate_remote_delete_without_opt_in() {
    String realmId = createRealm("scimoutbound4");
    String userId = createUser(realmId, "no-remote-delete-user", "no-remote-delete@example.com", true);

    String targetId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name",
                    "no-remote-delete-target",
                    "baseUrl",
                    "https://scim.example.test/scim/v2",
                    "bearerToken",
                    "no-remote-delete-secret",
                    "enabled",
                    true,
                    "deleteOnLocalDelete",
                    false))
            .when()
            .post("/admin/realms/" + realmId + "/scim/outbound-targets")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("deleteOnLocalDelete", equalTo(false))
            .extract()
            .response()
            .jsonPath()
            .getString("id");

    adminRequest()
        .when()
        .post("/admin/realms/" + realmId + "/scim/outbound-targets/" + targetId + "/sync-users")
        .then()
        .statusCode(200)
        .body("processedUsers", equalTo(1));

    adminRequest()
        .when()
        .delete("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(204);

    org.junit.jupiter.api.Assertions.assertFalse(
        TestScimOutboundConnector.wasDeleted(UUID.fromString(targetId), userId));
    org.junit.jupiter.api.Assertions.assertNotNull(
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId));
  }

  @Test
  void auto_sync_target_pushes_local_user_create_and_update_flows() {
    String realmName = "scimoutbound5";
    String realmId = createRealm(realmName);

    String targetId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name",
                    "auto-sync-target",
                    "baseUrl",
                    "https://scim.example.test/scim/v2",
                    "bearerToken",
                    "auto-sync-secret",
                    "enabled",
                    true,
                    "syncOnUserChange",
                    true))
            .when()
            .post("/admin/realms/" + realmId + "/scim/outbound-targets")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("syncOnUserChange", equalTo(true))
            .extract()
            .response()
            .jsonPath()
            .getString("id");

    String userId = createUser(realmId, "auto-sync-user", "auto-sync@example.com", true);
    Map<String, Object> createPayload =
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId);
    org.junit.jupiter.api.Assertions.assertNotNull(createPayload);
    org.junit.jupiter.api.Assertions.assertEquals("auto-sync-user", createPayload.get("userName"));
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, createPayload.get("active"));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "auto-sync-admin@example.com", "enabled", false))
        .when()
        .put("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(204);

    Map<String, Object> updatedPayload =
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId);
    org.junit.jupiter.api.Assertions.assertNotNull(updatedPayload);
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, updatedPayload.get("active"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> updatedEmails =
        (List<Map<String, Object>>) updatedPayload.get("emails");
    org.junit.jupiter.api.Assertions.assertEquals(
        "auto-sync-admin@example.com", updatedEmails.get(0).get("value"));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("enabled", true))
        .when()
        .put("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(204);
    setPassword(realmId, userId, "Secret123!");
    String token = accessToken(realmName, "auto-sync-user", "Secret123!");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body(Map.of("email", "auto-sync-account@example.com"))
        .when()
        .put("/account/profile")
        .then()
        .statusCode(204);

    Map<String, Object> accountPayload =
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId);
    org.junit.jupiter.api.Assertions.assertNotNull(accountPayload);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> accountEmails =
        (List<Map<String, Object>>) accountPayload.get("emails");
    org.junit.jupiter.api.Assertions.assertEquals(
        "auto-sync-account@example.com", accountEmails.get(0).get("value"));
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, accountPayload.get("active"));
  }

  @Test
  void targets_without_auto_sync_keep_user_changes_manual() {
    String realmId = createRealm("scimoutbound6");

    String targetId =
        adminRequest()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "name",
                    "manual-only-target",
                    "baseUrl",
                    "https://scim.example.test/scim/v2",
                    "bearerToken",
                    "manual-only-secret",
                    "enabled",
                    true,
                    "syncOnUserChange",
                    false))
            .when()
            .post("/admin/realms/" + realmId + "/scim/outbound-targets")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("syncOnUserChange", equalTo(false))
            .extract()
            .response()
            .jsonPath()
            .getString("id");

    String userId = createUser(realmId, "manual-only-user", "manual-only@example.com", true);
    org.junit.jupiter.api.Assertions.assertNull(
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "manual-only-updated@example.com", "enabled", false))
        .when()
        .put("/admin/realms/" + realmId + "/users/" + userId)
        .then()
        .statusCode(204);

    org.junit.jupiter.api.Assertions.assertNull(
        TestScimOutboundConnector.payload(UUID.fromString(targetId), userId));
  }
}
