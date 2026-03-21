package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import com.openidentity.service.JwtKeyService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@QuarkusTest
public class SamlAuthnRequestSigningTest {
  @Inject JwtKeyService jwtKeyService;

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void signed_authn_request_is_emitted_when_provider_requires_it() throws Exception {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-signed-request", "displayName", "SAML Signed Request"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "clientId", "saml-signed-request-client",
            "protocol", "openid-connect",
            "publicClient", true,
            "redirectUris", java.util.List.of("http://client.example/saml/signed-request-callback"),
            "grantTypes", java.util.List.of("authorization_code")))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

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

    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "saml-signed-request-client")
        .queryParam("redirect_uri", "http://client.example/saml/signed-request-callback")
        .queryParam("scope", "openid")
        .queryParam("state", "signed-request-state")
        .queryParam("code_challenge", com.openidentity.service.OidcGrantService.codeChallenge("signed-request-verifier-123456789"))
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/saml-signed-request/broker/saml/signed-idp/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String samlRequest = queryValue(URI.create(brokerStart.getHeader("Location")), "SAMLRequest");
    Document document = parse(Base64.getDecoder().decode(samlRequest));
    Element root = document.getDocumentElement();
    root.setIdAttribute("ID", true);
    NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
    if (signatures.getLength() == 0) {
      throw new AssertionError("Signed AuthnRequest is missing XML Signature");
    }
    DOMValidateContext validateContext = new DOMValidateContext(jwtKeyService.getPublicKey(), signatures.item(0));
    validateContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
    XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(validateContext);
    if (!signature.validate(validateContext)) {
      throw new AssertionError("AuthnRequest signature validation failed");
    }
  }

  private Document parse(byte[] bytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
  }

  private String queryValue(URI uri, String key) {
    if (uri.getRawQuery() == null) {
      return null;
    }
    for (String part : uri.getRawQuery().split("&")) {
      if (part.startsWith(key + "=")) {
        return java.net.URLDecoder.decode(part.substring(key.length() + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }
}
