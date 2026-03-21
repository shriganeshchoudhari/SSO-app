package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import com.openidentity.service.OidcGrantService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SamlBrokerFlowTest {
  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void saml_broker_login_returns_local_authorization_code() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-live", "displayName", "SAML Live"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "saml-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/saml/callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code", "refresh_token"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "nameIdFormat", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
            "syncAttributesOnLogin", true,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .when().get("/auth/realms/saml-live/broker/saml/adfs/metadata")
        .then().statusCode(200)
        .body(containsString("EntityDescriptor"))
        .body(containsString("/auth/realms/saml-live/broker/saml/adfs/acs"));

    String codeVerifier = "saml-code-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "saml-web")
        .queryParam("redirect_uri", "http://client.example/saml/callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "saml-app-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/saml-live/broker/saml/adfs/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI samlRedirect = URI.create(brokerStart.getHeader("Location"));
    String relayState = queryValue(samlRedirect, "RelayState");
    String samlRequest = queryValue(samlRedirect, "SAMLRequest");
    if (relayState == null || samlRequest == null) {
      throw new AssertionError("SAML broker redirect is missing RelayState or SAMLRequest");
    }
    String authnRequestId = authnRequestId(samlRequest);
    String acsDestination = authnRequestAcs(samlRequest);
    String spEntityId = authnRequestIssuer(samlRequest);

    Response acsResponse = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", relayState)
        .formParam("SAMLResponse", samlResponse(
            "https://adfs.example.com/adfs/services/trust",
            spEntityId,
            acsDestination,
            authnRequestId,
            "saml-user-123",
            "samluser",
            "samluser@example.com",
            true))
        .when().post("/auth/realms/saml-live/broker/saml/adfs/acs")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI clientRedirect = URI.create(acsResponse.getHeader("Location"));
    String localCode = queryValue(clientRedirect, "code");
    String returnedState = queryValue(clientRedirect, "state");
    if (localCode == null) {
      throw new AssertionError("local authorization code missing from SAML client redirect");
    }

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "saml-web")
        .formParam("code", localCode)
        .formParam("redirect_uri", "http://client.example/saml/callback")
        .formParam("code_verifier", codeVerifier)
        .when().post("/auth/realms/saml-live/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", notNullValue())
        .body("id_token", notNullValue());

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("username", hasItem("samluser"))
        .body("email", hasItem("samluser@example.com"))
        .body("federationSource", hasItem("saml"));

    if (!"saml-app-state".equals(returnedState)) {
      throw new AssertionError("original client state was not preserved for SAML broker flow");
    }
  }

  @Test
  void saml_broker_rejects_response_with_wrong_issuer() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-invalid", "displayName", "SAML Invalid"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "saml-invalid-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/saml/invalid-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    String codeVerifier = "saml-invalid-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "saml-invalid-web")
        .queryParam("redirect_uri", "http://client.example/saml/invalid-callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "saml-invalid-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/saml-invalid/broker/saml/adfs/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI samlRedirect = URI.create(brokerStart.getHeader("Location"));
    String relayState = queryValue(samlRedirect, "RelayState");
    String samlRequest = queryValue(samlRedirect, "SAMLRequest");
    String authnRequestId = authnRequestId(samlRequest);
    String acsDestination = authnRequestAcs(samlRequest);
    String spEntityId = authnRequestIssuer(samlRequest);

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", relayState)
        .formParam("SAMLResponse", samlResponse(
            "https://unexpected-idp.example.com/issuer",
            spEntityId,
            acsDestination,
            authnRequestId,
            "saml-user-999",
            "wrongissuer",
            "wrongissuer@example.com",
            true))
        .when().post("/auth/realms/saml-invalid/broker/saml/adfs/acs")
        .then().statusCode(400)
        .body(containsString("invalid_request"));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("username", not(hasItem("wrongissuer")));
  }

  @Test
  void saml_managed_user_can_be_detached_to_local_account() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-detach", "displayName", "SAML Detach"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "saml-detach-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/saml/detach-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code", "password"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "nameIdFormat", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
            "syncAttributesOnLogin", true,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    String codeVerifier = "saml-detach-code-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);

    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "saml-detach-web")
        .queryParam("redirect_uri", "http://client.example/saml/detach-callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "saml-detach-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/saml-detach/broker/saml/adfs/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI samlRedirect = URI.create(brokerStart.getHeader("Location"));
    String relayState = queryValue(samlRedirect, "RelayState");
    String samlRequest = queryValue(samlRedirect, "SAMLRequest");
    String authnRequestId = authnRequestId(samlRequest);
    String acsDestination = authnRequestAcs(samlRequest);
    String spEntityId = authnRequestIssuer(samlRequest);

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", relayState)
        .formParam("SAMLResponse", samlResponse(
            "https://adfs.example.com/adfs/services/trust",
            spEntityId,
            acsDestination,
            authnRequestId,
            "saml-detach-user-123",
            "samluser",
            "samluser@example.com",
            true))
        .when().post("/auth/realms/saml-detach/broker/saml/adfs/acs")
        .then().statusCode(anyOf(is(302), is(303)));

    Response usersResponse = adminRequest()
        .when().get("/admin/realms/" + realmId + "/users")
        .then().statusCode(200)
        .body("[0].federationSource", equalTo("saml"))
        .extract().response();
    String userId = usersResponse.jsonPath().getString("[0].id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("password", "DetachLocal123!"))
        .when().post("/admin/realms/" + realmId + "/users/" + userId + "/detach-federation")
        .then().statusCode(200)
        .body("federationSource", equalTo(null));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("email", "detached-saml@example.com", "enabled", true))
        .when().put("/admin/realms/" + realmId + "/users/" + userId)
        .then().statusCode(anyOf(is(200), is(204)));

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "password")
        .formParam("client_id", "saml-detach-web")
        .formParam("username", "samluser")
        .formParam("password", "DetachLocal123!")
        .when().post("/auth/realms/saml-detach/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", notNullValue());
  }

  private String samlResponse(
      String issuer,
      String audience,
      String destination,
      String inResponseTo,
      String subject,
      String username,
      String email,
      boolean emailVerified) {
    String notBefore = Instant.now().minusSeconds(30).toString();
    String notOnOrAfter = Instant.now().plusSeconds(300).toString();
    return Base64.getEncoder().encodeToString(("""
        <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            Destination="%s"
            InResponseTo="%s">
          <saml:Issuer>%s</saml:Issuer>
          <samlp:Status>
            <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
          </samlp:Status>
          <saml:Assertion>
            <saml:Issuer>%s</saml:Issuer>
            <saml:Conditions NotBefore="%s" NotOnOrAfter="%s">
              <saml:AudienceRestriction>
                <saml:Audience>%s</saml:Audience>
              </saml:AudienceRestriction>
            </saml:Conditions>
            <saml:Subject>
              <saml:NameID>%s</saml:NameID>
              <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
                <saml:SubjectConfirmationData Recipient="%s" NotOnOrAfter="%s"/>
              </saml:SubjectConfirmation>
            </saml:Subject>
            <saml:AttributeStatement>
              <saml:Attribute Name="username"><saml:AttributeValue>%s</saml:AttributeValue></saml:Attribute>
              <saml:Attribute Name="email"><saml:AttributeValue>%s</saml:AttributeValue></saml:Attribute>
              <saml:Attribute Name="email_verified"><saml:AttributeValue>%s</saml:AttributeValue></saml:Attribute>
            </saml:AttributeStatement>
          </saml:Assertion>
        </samlp:Response>
        """.formatted(
        destination,
        inResponseTo,
        issuer,
        issuer,
        notBefore,
        notOnOrAfter,
        audience,
        subject,
        destination,
        notOnOrAfter,
        username,
        email,
        Boolean.toString(emailVerified))).getBytes(StandardCharsets.UTF_8));
  }

  private String authnRequestId(String encodedSamlRequest) {
    return requestField(encodedSamlRequest, Pattern.compile("ID=\"([^\"]+)\""), "AuthnRequest ID");
  }

  private String authnRequestAcs(String encodedSamlRequest) {
    return requestField(encodedSamlRequest, Pattern.compile("AssertionConsumerServiceURL=\"([^\"]+)\""), "ACS URL");
  }

  private String authnRequestIssuer(String encodedSamlRequest) {
    return requestField(encodedSamlRequest, Pattern.compile("<saml:Issuer>([^<]+)</saml:Issuer>"), "AuthnRequest issuer");
  }

  private String requestField(String encodedSamlRequest, Pattern pattern, String label) {
    String xml = new String(Base64.getDecoder().decode(encodedSamlRequest), StandardCharsets.UTF_8);
    Matcher matcher = pattern.matcher(xml);
    if (!matcher.find()) {
      throw new AssertionError(label + " missing from broker request");
    }
    return matcher.group(1);
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
