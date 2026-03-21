package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LdapFederationAuthTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void password_grant_falls_back_to_ldap_and_provisions_user() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ldap-auth", "displayName", "LDAP Auth"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://mock-directory",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "BindSecret123!",
            "userSearchBase", "ou=users,dc=example,dc=com",
            "userSearchFilter", "(uid={0})",
            "usernameAttribute", "uid",
            "emailAttribute", "mail",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "ldapuser")
        .formParam("password", "DirectorySecret123!")
        .when().post("/auth/realms/ldap-auth/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(nullValue()));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("username", hasItem("ldapuser"))
        .body("email", hasItem("ldapuser@example.com"))
        .body("federationSource", hasItem("ldap"));
  }

  @Test
  void ldap_fallback_updates_existing_local_user_without_password() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ldap-existing", "displayName", "LDAP Existing"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "existing-ldap-user", "email", "stale@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://mock-directory",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "BindSecret123!",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "existing-ldap-user")
        .formParam("password", "ExistingDirectorySecret123!")
        .when().post("/auth/realms/ldap-existing/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(nullValue()));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].email", equalTo("existing-ldap@example.com"))
        .body("[0].federationSource", equalTo("ldap"));
  }

  @Test
  void ldap_profile_sync_can_be_disabled_per_provider() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ldap-no-sync", "displayName", "LDAP No Sync"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "existing-ldap-user", "email", "keepme@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://mock-directory",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "BindSecret123!",
            "syncAttributesOnLogin", false,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "existing-ldap-user")
        .formParam("password", "ExistingDirectorySecret123!")
        .when().post("/auth/realms/ldap-no-sync/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(nullValue()));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].email", equalTo("keepme@example.com"))
        .body("[0].federationSource", equalTo("ldap"));
  }

  @Test
  void missing_directory_user_can_disable_linked_account() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ldap-missing", "displayName", "LDAP Missing"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response providerResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://mock-directory",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "BindSecret123!",
            "disableMissingUsers", true,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String providerId = providerResp.jsonPath().getString("id");

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "ldapuser")
        .formParam("password", "DirectorySecret123!")
        .when().post("/auth/realms/ldap-missing/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", not(nullValue()));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userSearchBase", "missing-users"))
        .when().put("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "ldapuser")
        .formParam("password", "DirectorySecret123!")
        .when().post("/auth/realms/ldap-missing/protocol/openid-connect/token")
        .then().statusCode(401);

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].enabled", equalTo(false))
        .body("[0].federationSource", equalTo("ldap"));
  }

  @Test
  void manual_reconcile_updates_or_disables_linked_users() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ldap-reconcile", "displayName", "LDAP Reconcile"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response providerResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://mock-directory",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "BindSecret123!",
            "disableMissingUsers", true,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String providerId = providerResp.jsonPath().getString("id");

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "ldapuser")
        .formParam("password", "DirectorySecret123!")
        .when().post("/auth/realms/ldap-reconcile/protocol/openid-connect/token")
        .then().statusCode(200);

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("userSearchBase", "missing-users"))
        .when().put("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().post("/admin/realms/" + realmId + "/federation/ldap/" + providerId + "/reconcile")
        .then().statusCode(200)
        .body("checkedUsers", equalTo(1))
        .body("disabledUsers", equalTo(1));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].enabled", equalTo(false));
  }

  @Test
  void ldap_managed_users_reject_local_password_and_email_mutations() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "ldap-policy", "displayName", "LDAP Policy"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://mock-directory",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "BindSecret123!",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)));

    String accessToken = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "ldapuser")
        .formParam("password", "DirectorySecret123!")
        .when().post("/auth/realms/ldap-policy/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().jsonPath().getString("access_token");

    Response usersResponse = adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .extract().response();
    String userId = usersResponse.jsonPath().getString("[0].id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "NewLocalSecret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(409);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .contentType(ContentType.JSON)
        .body(Map.of("password", "AnotherLocalSecret123!"))
        .when().post("/account/credentials/password")
        .then().statusCode(409);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .contentType(ContentType.JSON)
        .body(Map.of("email", "changed@example.com"))
        .when().put("/account/profile")
        .then().statusCode(409);

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "adminchange@example.com", "enabled", true))
        .when().put("/admin/realms/" + realmId + "/users/" + userId)
        .then().statusCode(409);

    Response resetResponse = given()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "ldapuser@example.com"))
        .when().post("/auth/realms/ldap-policy/password-reset/request")
        .then().statusCode(anyOf(is(200), is(204)))
        .extract().response();

    String resetBody = resetResponse.getBody().asString();
    if (resetBody != null && !resetBody.isBlank()) {
      String resetToken = resetResponse.jsonPath().getString("token");
      if (resetToken != null) {
        throw new AssertionError("Federated users must not receive password reset tokens");
      }
    }
  }
}
