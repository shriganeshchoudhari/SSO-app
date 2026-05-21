package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
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

  private void setPassword(String realmId, String userId, String password) {
    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", password))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));
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

  @Test
  void organization_branding_is_persisted_and_applied_to_hosted_login() {
    String realmId = createRealm("orgrealmBrand");

    Response createResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "acme-brand",
            "displayName", "Acme Brand",
            "logoText", "AC",
            "primaryColor", "#123456",
            "accentColor", "#654321",
            "locale", "es"))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .body("logoText", equalTo("AC"))
        .body("primaryColor", equalTo("#123456"))
        .body("accentColor", equalTo("#654321"))
        .body("locale", equalTo("es"))
        .extract().response();

    String orgId = createResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "clientId", "brand-client",
            "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", List.of("https://brand.example.com/callback"),
            "grantTypes", List.of("authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/organizations/" + orgId)
        .then()
        .statusCode(200)
        .body("logoText", equalTo("AC"))
        .body("primaryColor", equalTo("#123456"))
        .body("accentColor", equalTo("#654321"))
        .body("locale", equalTo("es"));

    given()
        .when()
        .get("/auth/realms/orgrealmBrand/protocol/openid-connect/auth"
            + "?response_type=code"
            + "&client_id=brand-client"
            + "&redirect_uri=https://brand.example.com/callback"
            + "&scope=openid"
            + "&state=brand-state"
            + "&code_challenge=plain-challenge"
            + "&code_challenge_method=plain"
            + "&organization=acme-brand")
        .then()
        .statusCode(200)
        .body(containsString("lang=\"es\""))
        .body(containsString("Acme Brand"))
        .body(containsString("#123456"))
        .body(containsString("#654321"))
        .body(containsString(">AC<"));
  }

  @Test
  void organization_policy_enforces_membership_on_hosted_login() {
    String realmId = createRealm("orgrealmPolicyMember");
    String userId = createUser(realmId, "member-user");
    String outsiderId = createUser(realmId, "outsider-user");
    setPassword(realmId, userId, "Secret123!");
    setPassword(realmId, outsiderId, "Secret123!");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            Map.entry("clientId", "policy-client"),
            Map.entry("protocol", "openid-connect"),
            Map.entry("publicClient", true),
            Map.entry("redirectUris", List.of("https://policy.example.com/callback")),
            Map.entry("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    Response orgResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "acme-member",
            "displayName", "Acme Member",
            "requireMembershipForLogin", true,
            "allowedEmailDomains", List.of("example.com")))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String orgId = orgResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userId", userId, "orgRole", "member"))
        .when().post("/admin/realms/" + realmId + "/organizations/" + orgId + "/members")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "policy-client")
        .formParam("redirect_uri", "https://policy.example.com/callback")
        .formParam("scope", "openid")
        .formParam("state", "member-state")
        .formParam("code_challenge", "member-challenge")
        .formParam("code_challenge_method", "plain")
        .formParam("organization", "acme-member")
        .formParam("username", "member-user")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/orgrealmPolicyMember/protocol/openid-connect/auth")
        .then()
        .statusCode(anyOf(is(302), is(303)))
        .header("Location", containsString("code="))
        .header("Location", containsString("state=member-state"));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "policy-client")
        .formParam("redirect_uri", "https://policy.example.com/callback")
        .formParam("scope", "openid")
        .formParam("state", "outsider-state")
        .formParam("code_challenge", "outsider-challenge")
        .formParam("code_challenge_method", "plain")
        .formParam("organization", "acme-member")
        .formParam("username", "outsider-user")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/orgrealmPolicyMember/protocol/openid-connect/auth")
        .then()
        .statusCode(403)
        .body(containsString("You do not have access to this organization."));
  }

  @Test
  void organization_policy_enforces_allowed_email_domains_on_hosted_login() {
    String realmId = createRealm("orgrealmPolicyDomain");
    String goodUserId = createUser(realmId, "good-domain-user");
    String badUserId = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "bad-domain-user", "email", "bad-domain-user@other.test", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response().jsonPath().getString("id");
    setPassword(realmId, goodUserId, "Secret123!");
    setPassword(realmId, badUserId, "Secret123!");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            Map.entry("clientId", "domain-policy-client"),
            Map.entry("protocol", "openid-connect"),
            Map.entry("publicClient", true),
            Map.entry("redirectUris", List.of("https://domain.example.com/callback")),
            Map.entry("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "acme-domain",
            "displayName", "Acme Domain",
            "allowedEmailDomains", List.of("example.com")))
        .when().post("/admin/realms/" + realmId + "/organizations")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "domain-policy-client")
        .formParam("redirect_uri", "https://domain.example.com/callback")
        .formParam("scope", "openid")
        .formParam("state", "domain-good")
        .formParam("code_challenge", "domain-good-challenge")
        .formParam("code_challenge_method", "plain")
        .formParam("organization", "acme-domain")
        .formParam("username", "good-domain-user")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/orgrealmPolicyDomain/protocol/openid-connect/auth")
        .then()
        .statusCode(anyOf(is(302), is(303)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("response_type", "code")
        .formParam("client_id", "domain-policy-client")
        .formParam("redirect_uri", "https://domain.example.com/callback")
        .formParam("scope", "openid")
        .formParam("state", "domain-bad")
        .formParam("code_challenge", "domain-bad-challenge")
        .formParam("code_challenge_method", "plain")
        .formParam("organization", "acme-domain")
        .formParam("username", "bad-domain-user")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/orgrealmPolicyDomain/protocol/openid-connect/auth")
        .then()
        .statusCode(403)
        .body(containsString("You do not have access to this organization."));
  }
}
