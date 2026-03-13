package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OpenApiTest {
  @Test
  void openapi_contains_core_paths() {
    given()
      .when().get("/q/openapi?format=JSON")
      .then()
      .statusCode(200)
      .body("paths", notNullValue())
      .body("paths.keySet()", hasItems("/admin/realms", "/auth/realms/{realm}/protocol/openid-connect/token"));
  }
}

