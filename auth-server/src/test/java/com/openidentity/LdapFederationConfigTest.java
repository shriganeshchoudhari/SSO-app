package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LdapFederationConfigTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void ldap_provider_crud_flow() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "federation", "displayName", "Federation"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response providerResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "name", "corp-ldap",
            "url", "ldap://directory.internal:389",
            "bindDn", "cn=svc-openidentity,dc=example,dc=com",
            "bindCredential", "SecretBind123!",
            "userSearchBase", "ou=users,dc=example,dc=com",
            "userSearchFilter", "(uid={0})",
            "usernameAttribute", "uid",
            "emailAttribute", "mail",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(anyOf(is(200), is(201)))
        .body("name", equalTo("corp-ldap"))
        .body("bindCredentialConfigured", equalTo(true))
        .extract().response();
    String providerId = providerResp.jsonPath().getString("id");

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/federation/ldap")
        .then().statusCode(200)
        .body("name", hasItem("corp-ldap"));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("enabled", false, "userSearchFilter", "(sAMAccountName={0})"))
        .when().put("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then().statusCode(200)
        .body("enabled", equalTo(false))
        .body("userSearchFilter", equalTo("(sAMAccountName={0})"))
        .body("bindCredentialConfigured", equalTo(true));

    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/federation/ldap/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));
  }
}
