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
}
