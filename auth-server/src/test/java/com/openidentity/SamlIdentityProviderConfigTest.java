package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SamlIdentityProviderConfigTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void saml_identity_provider_crud_flow() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-config", "displayName", "SAML Config"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    Response providerResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "sloUrl", "https://adfs.example.com/adfs/ls/?wa=wsignout1.0",
            "x509Certificate", "-----BEGIN CERTIFICATE-----MIIB...-----END CERTIFICATE-----",
            "nameIdFormat", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
            "syncAttributesOnLogin", true,
            "wantAuthnRequestsSigned", false,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)))
        .body("alias", equalTo("adfs"))
        .body("x509CertificateConfigured", equalTo(true))
        .extract().response();
    String providerId = providerResp.jsonPath().getString("id");

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(200)
        .body("[0].entityId", equalTo("https://adfs.example.com/adfs/services/trust"));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("enabled", false, "wantAuthnRequestsSigned", true))
        .when().put("/admin/realms/" + realmId + "/brokering/saml/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/brokering/saml/" + providerId)
        .then().statusCode(200)
        .body("enabled", equalTo(false))
        .body("wantAuthnRequestsSigned", equalTo(true));

    adminRequest()
        .when().delete("/admin/realms/" + realmId + "/brokering/saml/" + providerId)
        .then().statusCode(anyOf(is(200), is(204)));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(200)
        .body("size()", equalTo(0));
  }

  @Test
  void saml_metadata_publishes_sp_signing_key() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-metadata", "displayName", "SAML Metadata"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "signed-idp",
            "entityId", "https://signed-idp.example.com/entity",
            "ssoUrl", "https://signed-idp.example.com/sso",
            "wantAuthnRequestsSigned", true,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .when().get("/auth/realms/saml-metadata/broker/saml/signed-idp/metadata")
        .then().statusCode(200)
        .body(containsString("KeyDescriptor use=\"signing\""))
        .body(containsString("RSAKeyValue"))
        .body(containsString("AssertionConsumerService"));
  }
}
