package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the SCIM 2.0 provisioning endpoints.
 */
@QuarkusTest
public class ScimProvisioningTest {

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  private String createRealm(String name) {
    Response r = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name, "displayName", name))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    return r.jsonPath().getString("id");
  }

  private String createPublicClient(String realmId, String clientId) {
    Response r = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "clientId", clientId,
            "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", List.of("http://localhost/callback"),
            "grantTypes", List.of("password", "authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    return r.jsonPath().getString("id");
  }

  private String createRole(String realmId, String name) {
    Response r = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name))
        .when().post("/admin/realms/" + realmId + "/roles")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    return r.jsonPath().getString("id");
  }

  private String createOrganization(String realmId, String name) {
    Response r = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name, "displayName", name))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    return r.jsonPath().getString("id");
  }

  private void addOrganizationMember(String realmId, String orgId, String userId) {
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", userId, "orgRole", "member"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(anyOf(is(200), is(201)));
  }

  private void updateScimDeprovisionMode(String realmId, String deprovisionMode) {
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("deprovisionMode", deprovisionMode))
        .when().put("/admin/realms/" + realmId + "/scim/settings")
        .then()
        .statusCode(200)
        .body("deprovisionMode", equalTo(deprovisionMode));
  }

  private void setPassword(String realmId, String userId, String password) {
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", password))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));
  }

  private String findLocalUserId(String realmId, String username) {
    Response response = adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .extract().response();
    List<Map<String, Object>> users = response.jsonPath().getList("$");
    return users.stream()
        .filter(user -> username.equals(user.get("username")))
        .map(user -> String.valueOf(user.get("id")))
        .findFirst()
        .orElse(null);
  }

  private String loginAndGetAccessToken(String realmName, String clientId, String username, String password) {
    Response r = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", clientId)
        .formParam("username", username)
        .formParam("password", password)
        .when().post("/auth/realms/" + realmName + "/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().response();
    return r.jsonPath().getString("access_token");
  }

  private List<String> jwtRoles(String accessToken) {
    String[] parts = accessToken.split("\\.");
    String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    return JsonPath.from(json).getList("roles");
  }

  @Test
  void service_provider_config_returns_supported_features() {
    createRealm("scimrealm1");
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/scimrealm1/ServiceProviderConfig")
        .then()
        .statusCode(200)
        .body("patch.supported",  equalTo(true))
        .body("bulk.supported",   equalTo(true))
        .body("bulk.maxOperations", equalTo(100))
        .body("filter.supported", equalTo(true));
  }

  @Test
  void create_list_get_patch_delete_scim_user() {
    String realmId = createRealm("scimrealm2");

    // Create
    Response create = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas",     java.util.List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
            "userName",    "jdoe",
            "displayName", "John Doe",
            "name",        Map.of("givenName", "John", "familyName", "Doe"),
            "emails",      java.util.List.of(Map.of("value", "jdoe@example.com", "primary", true)),
            "active",      true
        ))
        .when().post("/scim/v2/realms/scimrealm2/Users")
        .then()
        .statusCode(201)
        .body("id",          notNullValue())
        .body("userName",    equalTo("jdoe"))
        .body("displayName", equalTo("John Doe"))
        .body("active",      equalTo(true))
        .extract().response();

    String userId = create.jsonPath().getString("id");

    // Get by ID
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/scimrealm2/Users/" + userId)
        .then()
        .statusCode(200)
        .body("id",       equalTo(userId))
        .body("userName", equalTo("jdoe"));

    // List
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/scimrealm2/Users")
        .then()
        .statusCode(200)
        .body("totalResults",          greaterThanOrEqualTo(1))
        .body("Resources[0].userName", equalTo("jdoe"));

    // Filter
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .queryParam("filter", "userName eq \"jdoe\"")
        .when().get("/scim/v2/realms/scimrealm2/Users")
        .then()
        .statusCode(200)
        .body("totalResults",          equalTo(1))
        .body("Resources[0].userName", equalTo("jdoe"));

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .queryParam("filter", "emails.value eq \"jdoe@example.com\"")
        .when().get("/scim/v2/realms/scimrealm2/Users")
        .then()
        .statusCode(200)
        .body("totalResults", equalTo(1))
        .body("Resources[0].emails[0].value", equalTo("jdoe@example.com"));

    // Patch profile fields and username
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas",    java.util.List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
            "Operations", java.util.List.of(
                Map.of("op", "replace", "path", "userName", "value", "jdoe-renamed"),
                Map.of("op", "replace", "path", "displayName", "value", "John Queue"),
                Map.of("op", "replace", "path", "name.givenName", "value", "Johnny"),
                Map.of("op", "replace", "path", "name.familyName", "value", "Queue"),
                Map.of("op", "replace", "path", "emails.value", "value", "johnny@example.com"),
                Map.of("op", "replace", "path", "externalId", "value", "ext-jdoe"),
                Map.of("op", "replace", "path", "active", "value", false))
        ))
        .when().patch("/scim/v2/realms/scimrealm2/Users/" + userId)
        .then()
        .statusCode(200)
        .body("userName", equalTo("jdoe-renamed"))
        .body("displayName", equalTo("John Queue"))
        .body("name.givenName", equalTo("Johnny"))
        .body("name.familyName", equalTo("Queue"))
        .body("emails[0].value", equalTo("johnny@example.com"))
        .body("externalId", equalTo("ext-jdoe"))
        .body("active", equalTo(false));

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .queryParam("filter", "userName eq \"jdoe-renamed\"")
        .when().get("/scim/v2/realms/scimrealm2/Users")
        .then()
        .statusCode(200)
        .body("totalResults", equalTo(1))
        .body("Resources[0].userName", equalTo("jdoe-renamed"));

    org.junit.jupiter.api.Assertions.assertNotNull(findLocalUserId(realmId, "jdoe-renamed"));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/scim/settings")
        .then()
        .statusCode(200)
        .body("deprovisionMode", equalTo("disable"));

    // Delete
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().delete("/scim/v2/realms/scimrealm2/Users/" + userId)
        .then()
        .statusCode(204);

    String localUserId = findLocalUserId(realmId, "jdoe-renamed");
    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users/" + localUserId)
        .then()
        .statusCode(200)
        .body("enabled", equalTo(false));

    // Confirm gone
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/scimrealm2/Users/" + userId)
        .then()
        .statusCode(404);
  }

  @Test
  void scim_delete_mode_removes_linked_local_user_and_related_state() {
    String realmId = createRealm("scimrealm7");
    createPublicClient(realmId, "scim-delete-client");

    Response scimUserCreate = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
            "userName", "scim-delete-user",
            "displayName", "Delete Me",
            "emails", List.of(Map.of("value", "delete-me@example.com", "primary", true)),
            "active", true))
        .when().post("/scim/v2/realms/scimrealm7/Users")
        .then().statusCode(201)
        .extract().response();

    String scimUserId = scimUserCreate.jsonPath().getString("id");
    String localUserId = findLocalUserId(realmId, "scim-delete-user");
    String orgId = createOrganization(realmId, "scim-delete-org");
    addOrganizationMember(realmId, orgId, localUserId);
    setPassword(realmId, localUserId, "DeletePass123!");

    String accessToken =
        loginAndGetAccessToken("scimrealm7", "scim-delete-client", "scim-delete-user", "DeletePass123!");
    org.junit.jupiter.api.Assertions.assertNotNull(accessToken);

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then()
        .statusCode(200)
        .body("findAll { it.userId == '" + localUserId + "' }.size()", equalTo(1));

    updateScimDeprovisionMode(realmId, "delete");

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().delete("/scim/v2/realms/scimrealm7/Users/" + scimUserId)
        .then().statusCode(204);

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users/" + localUserId)
        .then().statusCode(404);

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then()
        .statusCode(200)
        .body("$", empty());

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then()
        .statusCode(200)
        .body("findAll { it.userId == '" + localUserId + "' }.size()", equalTo(0));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/events/logins")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].userId", nullValue());
  }

  @Test
  void duplicate_username_returns_conflict() {
    createRealm("scimrealm3");
    Map<String, Object> body = Map.of(
        "schemas",  java.util.List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
        "userName", "dupuser"
    );
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(body)
        .when().post("/scim/v2/realms/scimrealm3/Users")
        .then().statusCode(201);

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(body)
        .when().post("/scim/v2/realms/scimrealm3/Users")
        .then().statusCode(409);
  }

  @Test
  void unknown_realm_returns_404() {
    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/does-not-exist/Users")
        .then().statusCode(404);
  }

  @Test
  void create_list_replace_delete_scim_group() {
    createRealm("scimrealm4");

    Response user = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
            "userName", "group-user"))
        .when().post("/scim/v2/realms/scimrealm4/Users")
        .then().statusCode(201)
        .extract().response();

    String scimUserId = user.jsonPath().getString("id");
    String secondScimUserId = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
            "userName", "group-user-2"))
        .when().post("/scim/v2/realms/scimrealm4/Users")
        .then().statusCode(201)
        .extract().response()
        .jsonPath().getString("id");

    Response createGroup = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"),
            "displayName", "engineering",
            "members", List.of(Map.of("value", scimUserId))))
        .when().post("/scim/v2/realms/scimrealm4/Groups")
        .then()
        .statusCode(201)
        .body("displayName", equalTo("engineering"))
        .body("members", hasSize(1))
        .extract().response();

    String groupId = createGroup.jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .queryParam("filter", "displayName co \"engine\"")
        .when().get("/scim/v2/realms/scimrealm4/Groups")
        .then()
        .statusCode(200)
        .body("totalResults", greaterThanOrEqualTo(1))
        .body("Resources[0].displayName", equalTo("engineering"));

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
            "Operations", List.of(
                Map.of("op", "replace", "path", "displayName", "value", "platform-team"),
                Map.of("op", "add", "path", "members", "value", List.of(Map.of("value", secondScimUserId))),
                Map.of("op", "remove", "path", "members[value eq \"" + scimUserId + "\"]"))))
        .when().patch("/scim/v2/realms/scimrealm4/Groups/" + groupId)
        .then()
        .statusCode(200)
        .body("displayName", equalTo("platform-team"))
        .body("members", hasSize(1))
        .body("members[0].value", equalTo(secondScimUserId));

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .queryParam("filter", "displayName eq \"platform-team\"")
        .when().get("/scim/v2/realms/scimrealm4/Groups")
        .then()
        .statusCode(200)
        .body("totalResults", equalTo(1))
        .body("Resources[0].displayName", equalTo("platform-team"));

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"),
            "displayName", "engineering-updated",
            "members", List.of()))
        .when().put("/scim/v2/realms/scimrealm4/Groups/" + groupId)
        .then()
        .statusCode(200)
        .body("displayName", equalTo("engineering-updated"))
        .body("members", empty());

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().delete("/scim/v2/realms/scimrealm4/Groups/" + groupId)
        .then().statusCode(204);

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/scimrealm4/Groups/" + groupId)
        .then().statusCode(404);
  }

  @Test
  void scim_user_links_local_user_and_group_mapping_grants_effective_role() {
    String realmId = createRealm("scimrealm5");
    createPublicClient(realmId, "scim-client");
    String roleId = createRole(realmId, "scim-reader");

    Response scimUserCreate = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
            "userName", "scim-linked",
            "displayName", "SCIM Linked",
            "emails", List.of(Map.of("value", "scim-linked@example.com", "primary", true)),
            "active", true))
        .when().post("/scim/v2/realms/scimrealm5/Users")
        .then().statusCode(201)
        .extract().response();

    String scimUserId = scimUserCreate.jsonPath().getString("id");
    String localUserId = findLocalUserId(realmId, "scim-linked");

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users/" + localUserId)
        .then()
        .statusCode(200)
        .body("email", equalTo("scim-linked@example.com"))
        .body("enabled", equalTo(true));

    setPassword(realmId, localUserId, "ScimPass123!");

    Response groupCreate = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"),
            "displayName", "directory-engineers",
            "members", List.of(Map.of("value", scimUserId))))
        .when().post("/scim/v2/realms/scimrealm5/Groups")
        .then().statusCode(201)
        .extract().response();

    String groupId = groupCreate.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("groupId", groupId, "roleId", roleId))
        .when().post("/admin/realms/" + realmId + "/scim/group-role-mappings")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users/" + localUserId + "/roles")
        .then()
        .statusCode(200)
        .body("name", hasItem("scim-reader"));

    String accessToken = loginAndGetAccessToken("scimrealm5", "scim-client", "scim-linked", "ScimPass123!");
    org.junit.jupiter.api.Assertions.assertTrue(jwtRoles(accessToken).contains("scim-reader"));

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"),
            "displayName", "directory-engineers",
            "members", List.of()))
        .when().put("/scim/v2/realms/scimrealm5/Groups/" + groupId)
        .then().statusCode(200);

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users/" + localUserId + "/roles")
        .then()
        .statusCode(200)
        .body("name", not(hasItem("scim-reader")));
  }

  @Test
  void bulk_provisions_users_and_groups_with_bulk_references() {
    String realmId = createRealm("scimrealm6");

    Response bulkResponse = given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .contentType("application/scim+json")
        .body(Map.of(
            "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:BulkRequest"),
            "failOnErrors", 1,
            "Operations", List.of(
                Map.of(
                    "method", "POST",
                    "bulkId", "user-1",
                    "path", "/Users",
                    "data", Map.of(
                        "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
                        "userName", "bulk-user",
                        "displayName", "Bulk User",
                        "emails", List.of(Map.of("value", "bulk-user@example.com", "primary", true)),
                        "active", true)),
                Map.of(
                    "method", "POST",
                    "bulkId", "group-1",
                    "path", "/Groups",
                    "data", Map.of(
                        "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"),
                        "displayName", "bulk-engineering",
                        "members", List.of(Map.of("value", "bulkId:user-1")))),
                Map.of(
                    "method", "PATCH",
                    "path", "/Groups/bulkId:group-1",
                    "data", Map.of(
                        "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                        "Operations", List.of(
                            Map.of("op", "replace", "path", "displayName", "value", "bulk-engineering-updated")))))))
        .when().post("/scim/v2/realms/scimrealm6/Bulk")
        .then()
        .statusCode(200)
        .body("Operations", hasSize(3))
        .body("Operations[0].status", equalTo("201"))
        .body("Operations[1].status", equalTo("201"))
        .body("Operations[2].status", equalTo("200"))
        .extract().response();

    String scimUserId = bulkResponse.jsonPath().getString("Operations[0].response.id");
    String groupId = bulkResponse.jsonPath().getString("Operations[1].response.id");

    given()
        .header("Authorization", "Bearer test-bootstrap-token")
        .when().get("/scim/v2/realms/scimrealm6/Groups/" + groupId)
        .then()
        .statusCode(200)
        .body("displayName", equalTo("bulk-engineering-updated"))
        .body("members[0].value", equalTo(scimUserId));

    org.junit.jupiter.api.Assertions.assertNotNull(findLocalUserId(realmId, "bulk-user"));
  }
}
