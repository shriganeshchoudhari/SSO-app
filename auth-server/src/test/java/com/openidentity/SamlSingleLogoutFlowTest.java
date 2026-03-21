package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.openidentity.service.OidcGrantService;
import com.openidentity.service.JwtKeyService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@QuarkusTest
public class SamlSingleLogoutFlowTest {
  @Inject JwtKeyService jwtKeyService;

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void saml_broker_single_logout_clears_session_and_redirects_back() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-slo", "displayName", "SAML SLO"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "saml-slo-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/saml/slo-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "sloUrl", "https://adfs.example.com/adfs/logout",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    given()
        .when().get("/auth/realms/saml-slo/broker/saml/adfs/metadata")
        .then().statusCode(200)
        .body(containsString("SingleLogoutService"))
        .body(containsString("/auth/realms/saml-slo/broker/saml/adfs/slo"));

    String codeVerifier = "saml-slo-code-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);
    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", "saml-slo-web")
        .queryParam("redirect_uri", "http://client.example/saml/slo-callback")
        .queryParam("scope", "openid profile email")
        .queryParam("state", "saml-slo-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/saml-slo/broker/saml/adfs/login")
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
            "saml-user-slo-123",
            "samlslouser",
            "samlslo@example.com",
            true))
        .when().post("/auth/realms/saml-slo/broker/saml/adfs/acs")
        .then().statusCode(anyOf(is(302), is(303)));

    Response sessions = adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then().statusCode(200)
        .body("size()", equalTo(1))
        .extract().response();
    String sid = sessions.jsonPath().getString("[0].id");

    Response logoutStart = given()
        .redirects().follow(false)
        .queryParam("sid", sid)
        .queryParam("post_logout_redirect_uri", "http://client.example/post-logout")
        .when().get("/auth/realms/saml-slo/broker/saml/adfs/logout")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI logoutRedirect = URI.create(logoutStart.getHeader("Location"));
    String logoutRelayState = queryValue(logoutRedirect, "RelayState");
    String logoutRequest = queryValue(logoutRedirect, "SAMLRequest");
    String logoutRequestId = logoutRequestId(logoutRequest);
    String logoutRequestIssuer = logoutRequestIssuer(logoutRequest);
    String logoutSessionIndex = logoutRequestSessionIndex(logoutRequest);

    if (!"https://adfs.example.com/adfs/logout".equals(logoutRedirect.getScheme() + "://" + logoutRedirect.getHost() + logoutRedirect.getPath())) {
      throw new AssertionError("logout redirect did not target provider SLO endpoint");
    }
    if (!sid.equals(logoutSessionIndex)) {
      throw new AssertionError("logout request did not include local session id as SessionIndex");
    }
    if (!spEntityId.equals(logoutRequestIssuer)) {
      throw new AssertionError("logout request issuer did not match SP entity id");
    }

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", logoutRelayState)
        .formParam("SAMLResponse", logoutResponse(
            "https://adfs.example.com/adfs/services/trust",
            localUri("/auth/realms/saml-slo/broker/saml/adfs/slo"),
            logoutRequestId))
        .when().post("/auth/realms/saml-slo/broker/saml/adfs/slo")
        .then().statusCode(anyOf(is(302), is(303)))
        .header("Location", equalTo("http://client.example/post-logout"));

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then().statusCode(200)
        .body("size()", equalTo(0));
  }

  @Test
  void idp_initiated_logout_clears_matching_session_and_returns_logout_response() {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-idp-slo", "displayName", "SAML IdP SLO"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "saml-idp-slo-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/saml/idp-slo-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "sloUrl", "https://adfs.example.com/adfs/logout",
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    BrokerLoginSession session = establishBrokeredSession(
        realmId,
        "saml-idp-slo",
        "saml-idp-slo-web",
        "http://client.example/saml/idp-slo-callback",
        "saml-user-idp-slo-123",
        "samlidpslouser",
        "samlidpslo@example.com");

    String logoutRequestId = "_" + java.util.UUID.randomUUID();
    Response logoutResponse = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", "idp-logout-state")
        .formParam("SAMLRequest", logoutRequest(
            "https://adfs.example.com/adfs/services/trust",
            localUri("/auth/realms/saml-idp-slo/broker/saml/adfs/slo"),
            logoutRequestId,
            session.subject(),
            session.sid(),
            true))
        .when().post("/auth/realms/saml-idp-slo/broker/saml/adfs/slo")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    URI redirect = URI.create(logoutResponse.getHeader("Location"));
    if (!"https://adfs.example.com/adfs/logout".equals(redirect.getScheme() + "://" + redirect.getHost() + redirect.getPath())) {
      throw new AssertionError("IdP-initiated logout did not redirect to provider SLO endpoint");
    }
    String relayState = queryValue(redirect, "RelayState");
    String encodedLogoutResponse = queryValue(redirect, "SAMLResponse");
    if (!"idp-logout-state".equals(relayState) || encodedLogoutResponse == null) {
      throw new AssertionError("IdP-initiated logout response is missing RelayState or SAMLResponse");
    }

    String logoutResponseXml = new String(Base64.getDecoder().decode(encodedLogoutResponse), StandardCharsets.UTF_8);
    if (!logoutResponseXml.contains("LogoutResponse")) {
      throw new AssertionError("IdP-initiated logout did not return a LogoutResponse");
    }
    if (!logoutResponseXml.contains("InResponseTo=\"" + logoutRequestId + "\"")) {
      throw new AssertionError("LogoutResponse did not preserve the incoming request ID");
    }
    if (!logoutResponseXml.contains("<saml:Issuer>" + session.spEntityId() + "</saml:Issuer>")) {
      throw new AssertionError("LogoutResponse issuer did not match the SP entity ID");
    }

    adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then().statusCode(200)
        .body("size()", equalTo(0));
  }

  @Test
  void idp_initiated_logout_returns_signed_logout_response_when_provider_requires_signed_messages() throws Exception {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "saml-idp-slo-signed", "displayName", "SAML IdP SLO Signed"))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", "saml-idp-slo-signed-web"),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of("http://client.example/saml/idp-slo-signed-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", "adfs",
            "entityId", "https://adfs.example.com/adfs/services/trust",
            "ssoUrl", "https://adfs.example.com/adfs/ls/",
            "sloUrl", "https://adfs.example.com/adfs/logout",
            "wantAuthnRequestsSigned", true,
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    BrokerLoginSession session = establishBrokeredSession(
        realmId,
        "saml-idp-slo-signed",
        "saml-idp-slo-signed-web",
        "http://client.example/saml/idp-slo-signed-callback",
        "saml-user-idp-slo-signed-123",
        "samlidpslosigneduser",
        "samlidpslosigned@example.com");

    String logoutRequestId = "_" + java.util.UUID.randomUUID();
    Response logoutResponse = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", "idp-logout-signed-state")
        .formParam("SAMLRequest", logoutRequest(
            "https://adfs.example.com/adfs/services/trust",
            localUri("/auth/realms/saml-idp-slo-signed/broker/saml/adfs/slo"),
            logoutRequestId,
            session.subject(),
            session.sid(),
            true))
        .when().post("/auth/realms/saml-idp-slo-signed/broker/saml/adfs/slo")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String encodedLogoutResponse = queryValue(URI.create(logoutResponse.getHeader("Location")), "SAMLResponse");
    if (encodedLogoutResponse == null) {
      throw new AssertionError("signed IdP-initiated logout response is missing SAMLResponse");
    }

    Document document = parse(Base64.getDecoder().decode(encodedLogoutResponse));
    Element root = document.getDocumentElement();
    root.setIdAttribute("ID", true);
    NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
    if (signatures.getLength() == 0) {
      throw new AssertionError("signed logout response is missing XML Signature");
    }
    DOMValidateContext validateContext = new DOMValidateContext(jwtKeyService.getPublicKey(), signatures.item(0));
    validateContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
    XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(validateContext);
    if (!signature.validate(validateContext)) {
      throw new AssertionError("logout response signature validation failed");
    }
  }

  private BrokerLoginSession establishBrokeredSession(
      String realmId,
      String realmName,
      String clientId,
      String redirectUri,
      String subject,
      String username,
      String email) {
    String codeVerifier = "saml-brokered-session-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);
    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", redirectUri)
        .queryParam("scope", "openid profile email")
        .queryParam("state", "saml-session-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/" + realmName + "/broker/saml/adfs/login")
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
            subject,
            username,
            email,
            true))
        .when().post("/auth/realms/" + realmName + "/broker/saml/adfs/acs")
        .then().statusCode(anyOf(is(302), is(303)));

    Response sessions = adminRequest()
        .when().get("/admin/realms/" + realmId + "/sessions")
        .then().statusCode(200)
        .body("size()", equalTo(1))
        .extract().response();
    return new BrokerLoginSession(sessions.jsonPath().getString("[0].id"), subject, spEntityId);
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

  private String logoutResponse(String issuer, String destination, String inResponseTo) {
    String notOnOrAfter = Instant.now().plusSeconds(300).toString();
    return Base64.getEncoder().encodeToString(("""
        <samlp:LogoutResponse xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            Destination="%s"
            InResponseTo="%s"
            NotOnOrAfter="%s">
          <saml:Issuer>%s</saml:Issuer>
          <samlp:Status>
            <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
          </samlp:Status>
        </samlp:LogoutResponse>
        """.formatted(destination, inResponseTo, notOnOrAfter, issuer)).getBytes(StandardCharsets.UTF_8));
  }

  private String logoutRequest(
      String issuer,
      String destination,
      String requestId,
      String subject,
      String sessionIndex,
      boolean withExpiry) {
    String notOnOrAfter = withExpiry ? " NotOnOrAfter=\"" + Instant.now().plusSeconds(300) + "\"" : "";
    return Base64.getEncoder().encodeToString(("""
        <samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            ID="%s"
            Version="2.0"
            IssueInstant="%s"
            Destination="%s"%s>
          <saml:Issuer>%s</saml:Issuer>
          <saml:NameID>%s</saml:NameID>
          <samlp:SessionIndex>%s</samlp:SessionIndex>
        </samlp:LogoutRequest>
        """.formatted(
        requestId,
        Instant.now().toString(),
        destination,
        notOnOrAfter,
        issuer,
        subject,
        sessionIndex)).getBytes(StandardCharsets.UTF_8));
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

  private String logoutRequestId(String encodedLogoutRequest) {
    return requestField(encodedLogoutRequest, Pattern.compile("ID=\"([^\"]+)\""), "LogoutRequest ID");
  }

  private String logoutRequestIssuer(String encodedLogoutRequest) {
    return requestField(encodedLogoutRequest, Pattern.compile("<saml:Issuer>([^<]+)</saml:Issuer>"), "LogoutRequest issuer");
  }

  private String logoutRequestSessionIndex(String encodedLogoutRequest) {
    return requestField(encodedLogoutRequest, Pattern.compile("<samlp:SessionIndex>([^<]+)</samlp:SessionIndex>"), "LogoutRequest SessionIndex");
  }

  private String requestField(String encodedSamlRequest, Pattern pattern, String label) {
    String xml = new String(Base64.getDecoder().decode(encodedSamlRequest), StandardCharsets.UTF_8);
    Matcher matcher = pattern.matcher(xml);
    if (!matcher.find()) {
      throw new AssertionError(label + " missing from SAML request");
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

  private String localUri(String path) {
    int port = RestAssured.port > 0 ? RestAssured.port : 8081;
    return RestAssured.baseURI + ":" + port + path;
  }

  private Document parse(byte[] bytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
  }

  private record BrokerLoginSession(String sid, String subject, String spEntityId) {}
}
