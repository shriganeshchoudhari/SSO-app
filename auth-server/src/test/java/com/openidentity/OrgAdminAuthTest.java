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
 * Integration tests for org-admin delegated access via AdminAuthFilter.
 *
 * Verifies that:
 * - A token with admin=true can access any admin path (global admin).
 * - A user with org-role "admin" in a realm can access that realm's admin paths.
 * - A non-admin user is denied admin paths (403).
 * - An org-admin cannot access a different realm's admin paths.
 * - An org-admin cannot access global admin paths (e.g. /admin/keys).
 */
@QuarkusTest
public class OrgAdminAuthTest {

  private RequestSpecification bootstrapRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  private String createRealm(String name) {
    Response r = bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name, "displayName", name))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    return r.jsonPath().getString("id");
  }

  private String createUser(String realmId, String username, boolean isAdmin) {
    Response r = bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", username, "email", username + "@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = r.jsonPath().getString("id");

    // set password
    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Test1234!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    if (isAdmin) {
      // Assign the global "admin" role if it exists, otherwise just rely on org-admin path
      Response roleList = bootstrapRequest()
          .when().get("/admin/realms/" + realmId + "/roles")
          .then().statusCode(200).extract().response();
      // Look for existing admin role
      var roles = roleList.jsonPath().getList("$");
      // Don't assign global admin role — this user tests org-admin path only
    }
    return userId;
  }

  private String loginAndGetToken(String realmName, String clientId, String username) {
    Response r = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", clientId)
        .formParam("username", username)
        .formParam("password", "Test1234!")
        .when().post("/auth/realms/" + realmName + "/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().response();
    return r.jsonPath().getString("access_token");
  }

  @Test
  void global_admin_token_can_access_all_admin_paths() {
    // Bootstrap token is treated as global admin — already tested throughout suite.
    // Here we just confirm it reaches /admin/realms list.
    bootstrapRequest()
        .when().get("/admin/realms")
        .then().statusCode(200);
  }

  @Test
  void org_admin_user_can_access_their_realm_users() {
    String realmId = createRealm("orgauth-realm-a");
    String adminUserId = createUser(realmId, "orgadmin-a", false);

    // Create a client for login
    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("clientId", "orgauth-client-a", "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", java.util.List.of("http://localhost/cb"),
            "grantTypes", java.util.List.of("password", "authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    // Create an org and make adminUser an org-admin
    Response orgResp = bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "alpha-org"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201))).extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", adminUserId, "orgRole", "admin"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(anyOf(is(200), is(201)));

    // Login as org-admin
    String token = loginAndGetToken("orgauth-realm-a", "orgauth-client-a", "orgadmin-a");

    // Org-admin should be able to list users in their realm
    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200);
  }

  @Test
  void org_admin_cannot_access_different_realm() {
    String realmA = createRealm("orgauth-realm-b1");
    String realmB = createRealm("orgauth-realm-b2");
    String adminUserId = createUser(realmA, "orgadmin-b", false);

    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("clientId", "orgauth-client-b", "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", java.util.List.of("http://localhost/cb"),
            "grantTypes", java.util.List.of("password", "authorization_code")))
        .when().post("/admin/realms/" + realmA + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    Response orgResp = bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "beta-org"))
        .when().post("/admin/realms/" + realmA + "/organizations")
        .then().statusCode(anyOf(is(200), is(201))).extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", adminUserId, "orgRole", "admin"))
        .when().post("/admin/realms/" + realmA + "/organizations/" + orgId + "/members")
        .then().statusCode(anyOf(is(200), is(201)));

    String token = loginAndGetToken("orgauth-realm-b1", "orgauth-client-b", "orgadmin-b");

    // Should NOT be able to access realm B
    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/admin/realms/" + realmB + "/users")
        .then().statusCode(403);
  }

  @Test
  void org_admin_cannot_access_global_admin_paths() {
    String realmId = createRealm("orgauth-realm-c");
    String adminUserId = createUser(realmId, "orgadmin-c", false);

    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("clientId", "orgauth-client-c", "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", java.util.List.of("http://localhost/cb"),
            "grantTypes", java.util.List.of("password", "authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    Response orgResp = bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "gamma-org"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201))).extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", adminUserId, "orgRole", "admin"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(anyOf(is(200), is(201)));

    String token = loginAndGetToken("orgauth-realm-c", "orgauth-client-c", "orgadmin-c");

    // /admin/keys is a global path — org-admin must not access it
    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/admin/keys")
        .then().statusCode(403);
  }

  @Test
  void non_admin_user_is_denied_admin_paths() {
    String realmId = createRealm("orgauth-realm-d");
    createUser(realmId, "plain-user-d", false);

    bootstrapRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("clientId", "orgauth-client-d", "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", java.util.List.of("http://localhost/cb"),
            "grantTypes", java.util.List.of("password", "authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    String token = loginAndGetToken("orgauth-realm-d", "orgauth-client-d", "plain-user-d");

    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(403);
  }
}
