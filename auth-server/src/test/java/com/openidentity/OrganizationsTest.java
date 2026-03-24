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
 * Integration tests for organization CRUD and member management.
 */
@QuarkusTest
public class OrganizationsTest {

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

  private String createUser(String realmId, String username) {
    Response r = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", username, "email", username + "@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    return r.jsonPath().getString("id");
  }

  @Test
  void create_list_get_update_delete_organization() {
    String realmId = createRealm("orgrealmA");

    // Create
    Response createResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "acme-corp", "displayName", "Acme Corporation"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .body("id", notNullValue())
        .body("name", equalTo("acme-corp"))
        .body("displayName", equalTo("Acme Corporation"))
        .body("enabled", equalTo(true))
        .extract().response();

    String orgId = createResp.jsonPath().getString("id");

    // List
    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].name", equalTo("acme-corp"));

    // Get by ID
    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations/" + orgId)
        .then()
        .statusCode(200)
        .body("id", equalTo(orgId))
        .body("name", equalTo("acme-corp"));

    // Update
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("displayName", "Acme Corp Updated", "enabled", true))
        .when().put("/admin/realms/" + realmId + "/organizations/" + orgId)
        .then()
        .statusCode(200)
        .body("displayName", equalTo("Acme Corp Updated"));

    // Delete
    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/organizations/" + orgId)
        .then()
        .statusCode(204);

    // Confirm gone
    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations/" + orgId)
        .then()
        .statusCode(404);
  }

  @Test
  void duplicate_org_name_in_same_realm_returns_conflict() {
    String realmId = createRealm("orgrealmB");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "unique-org"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "unique-org"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(409);
  }

  @Test
  void add_list_remove_organization_members() {
    String realmId = createRealm("orgrealmC");
    String userId  = createUser(realmId, "org-member-user");

    Response orgResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "beta-inc"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201))).extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    // Add member
    Response memberResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", userId, "orgRole", "member"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .body("userId", equalTo(userId))
        .body("orgRole", equalTo("member"))
        .extract().response();

    String memberId = memberResp.jsonPath().getString("id");

    // List members
    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].userId", equalTo(userId));

    // Duplicate add returns conflict
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", userId, "orgRole", "member"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(409);

    // Remove
    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/organizations/" + orgId + "/members/" + memberId)
        .then().statusCode(204);

    // Confirm removed
    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(200).body("$", empty());
  }

  @Test
  void org_admin_role_can_be_assigned() {
    String realmId = createRealm("orgrealmD");
    String userId  = createUser(realmId, "org-admin-user");

    Response orgResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "gamma-llc"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201))).extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", userId, "orgRole", "admin"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .body("orgRole", equalTo("admin"));
  }

  @Test
  void invalid_org_role_returns_bad_request() {
    String realmId = createRealm("orgrealmE");
    String userId  = createUser(realmId, "bad-role-user");

    Response orgResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "delta-co"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201))).extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", userId, "orgRole", "superuser"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(400);
  }
}
