package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.openidentity.support.TestWebhookDispatcher;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WebhookDeliveryTest {
  @BeforeEach
  void resetDispatcher() {
    TestWebhookDispatcher.reset();
  }

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  private String createRealm(String name) {
    return adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name, "displayName", name))
        .when()
        .post("/admin/realms")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .extract()
        .jsonPath()
        .getString("id");
  }

  private String createUser(String realmId, String username, String email, boolean enabled) {
    return adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", username, "email", email, "enabled", enabled))
        .when()
        .post("/admin/realms/" + realmId + "/users")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .extract()
        .jsonPath()
        .getString("id");
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

  private String createWebhook(
      String realmId, String name, List<String> subscribedEvents, String signingSecret) {
    return adminRequest()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "name", name,
                "url", "https://hooks.example.test/" + name,
                "signingSecret", signingSecret,
                "subscribedEvents", subscribedEvents,
                "enabled", true))
        .when()
        .post("/admin/realms/" + realmId + "/webhooks")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .body("id", notNullValue())
        .extract()
        .jsonPath()
        .getString("id");
  }

  @Test
  void webhook_baseline_captures_admin_and_login_events() {
    String realmName = "webhooks1";
    String realmId = createRealm(realmName);
    String webhookId = createWebhook(realmId, "ops-stream", List.of("admin.user.*", "login.*"), "webhook-secret");
    UUID webhookUuid = UUID.fromString(webhookId);

    String userId = createUser(realmId, "hook-user", "hook-user@example.com", true);
    List<TestWebhookDispatcher.CapturedDispatch> initialDeliveries =
        TestWebhookDispatcher.deliveries(webhookUuid);
    Assertions.assertEquals(1, initialDeliveries.size());
    Assertions.assertEquals("admin.user.create", initialDeliveries.getFirst().eventType());
    Assertions.assertTrue(initialDeliveries.getFirst().requestBody().contains("\"resourceType\":\"user\""));
    Assertions.assertTrue(initialDeliveries.getFirst().requestBody().contains("\"details\":\"username=hook-user\""));
    Assertions.assertTrue(initialDeliveries.getFirst().signatureHeader().startsWith("sha256="));

    setPassword(realmId, userId, "Secret123!");

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "hook-user")
        .formParam("password", "Secret123!")
        .when()
        .post("/auth/realms/" + realmName + "/protocol/openid-connect/token")
        .then()
        .statusCode(200)
        .body("access_token", notNullValue());

    List<TestWebhookDispatcher.CapturedDispatch> allDeliveries =
        TestWebhookDispatcher.deliveries(webhookUuid);
    Assertions.assertEquals(2, allDeliveries.size());
    Assertions.assertEquals("login.login", allDeliveries.get(1).eventType());

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/webhooks/" + webhookId + "/deliveries")
        .then()
        .statusCode(200)
        .body("$", hasSize(2))
        .body("[0].responseStatus", equalTo(202))
        .body("[0].success", equalTo(true))
        .body("[1].eventType", equalTo("admin.user.create"));
  }

  @Test
  void webhook_subscriptions_filter_non_matching_events() {
    String realmName = "webhooks2";
    String realmId = createRealm(realmName);
    String webhookId = createWebhook(realmId, "logout-only", List.of("login.logout"), "logout-secret");
    UUID webhookUuid = UUID.fromString(webhookId);

    String userId = createUser(realmId, "logout-user", "logout-user@example.com", true);
    setPassword(realmId, userId, "Secret123!");

    String accessToken =
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "password")
            .formParam("client_id", "web-app")
            .formParam("username", "logout-user")
            .formParam("password", "Secret123!")
            .when()
            .post("/auth/realms/" + realmName + "/protocol/openid-connect/token")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("access_token");

    Assertions.assertTrue(TestWebhookDispatcher.deliveries(webhookUuid).isEmpty());

    String sessionId =
        given()
            .header("Authorization", "Bearer " + accessToken)
            .when()
            .get("/account/sessions")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("[0].id");

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("sid", sessionId)
        .when()
        .post("/auth/realms/" + realmName + "/protocol/openid-connect/logout")
        .then()
        .statusCode(anyOf(is(200), is(204)));

    List<TestWebhookDispatcher.CapturedDispatch> deliveries =
        TestWebhookDispatcher.deliveries(webhookUuid);
    Assertions.assertEquals(1, deliveries.size());
    Assertions.assertEquals("login.logout", deliveries.getFirst().eventType());

    adminRequest()
        .when()
        .get("/admin/realms/" + realmId + "/webhooks")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].subscribedEvents", hasSize(1))
        .body("[0].subscribedEvents[0]", equalTo("login.logout"));
  }
}
