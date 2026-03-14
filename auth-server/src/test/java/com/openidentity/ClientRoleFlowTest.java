package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ClientRoleFlowTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void client_crud_and_role_assign_flow() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "demo-client", "displayName", "Demo Client"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");
    if (realmId == null || realmId.isBlank()) {
      String loc = realmResp.getHeader("Location");
      realmId = loc != null ? loc.substring(loc.lastIndexOf('/')+1) : null;
    }

    Response userResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "bob", "email", "bob@example.com", "enabled", true))
        .when().post("/admin/realms/" + realmId + "/users")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String userId = userResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "Secret123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/credentials/password")
        .then().statusCode(anyOf(is(200), is(201), is(204)));

    Response clientResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("clientId", "web-app", "protocol", "openid-connect", "publicClient", true))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String clientId = clientResp.jsonPath().getString("id");

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/clients")
        .then().statusCode(200)
        .body("clientId", hasItem("web-app"));

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

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users/" + userId + "/roles")
        .then().statusCode(200)
        .body("name", hasItem("admin"));

    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/users/" + userId + "/roles/" + roleId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/roles/" + roleId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/clients/" + clientId)
        .then().statusCode(anyOf(is(200), is(204)));
  }
}
