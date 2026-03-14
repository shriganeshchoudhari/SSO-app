package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminSecurityTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void admin_endpoints_require_admin_or_bootstrap_token() {
    given()
        .when().get("/admin/realms")
        .then().statusCode(401);

    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "secure-admin", "displayName", "Secure Admin"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "operator", "email", "operator@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    String nonAdminToken = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "operator")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/secure-admin/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().jsonPath().getString("access_token");

    given()
        .header("Authorization", "Bearer " + nonAdminToken)
        .when().get("/admin/realms")
        .then().statusCode(403);

    Response roleResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "admin"))
        .when().post("/admin/realms/" + realmId + "/roles")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String roleId = roleResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/roles/" + roleId)
        .then().statusCode(anyOf(is(200), is(204)));

    String adminToken = given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "web-app")
        .formParam("username", "operator")
        .formParam("password", "Secret123!")
        .when().post("/auth/realms/secure-admin/protocol/openid-connect/token")
        .then().statusCode(200)
        .extract().jsonPath().getString("access_token");

    given()
        .header("Authorization", "Bearer " + adminToken)
        .when().get("/admin/realms")
        .then().statusCode(200);
  }
}
