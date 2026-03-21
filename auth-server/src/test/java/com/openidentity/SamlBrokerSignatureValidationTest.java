package com.openidentity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.openidentity.service.OidcGrantService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@QuarkusTest
public class SamlBrokerSignatureValidationTest {
  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  private RequestSpecification adminRequest() {
    return given().header("Authorization", "Bearer test-bootstrap-token");
  }

  @Test
  void saml_broker_accepts_signed_response_when_certificate_configured() throws Exception {
    SigningMaterial signingMaterial = signingMaterial();
    RealmSetup setup = realmWithSignedSamlProvider("saml-signed", "saml-signed-web", "signed-idp", signingMaterial);

    String codeVerifier = "saml-signed-verifier-123456789";
    String codeChallenge = OidcGrantService.codeChallenge(codeVerifier);
    BrokerRequest request = brokerRequest("saml-signed", "signed-idp", "saml-signed-web", "http://client.example/saml/signed-callback", codeChallenge);

    String signedResponse = signedSamlResponse(
        signingMaterial.privateKey(),
        "https://signed-idp.example.com/entity",
        request.spEntityId(),
        request.acsDestination(),
        request.authnRequestId(),
        "signed-user-123",
        "signeduser",
        "signeduser@example.com");

    Response acsResponse = given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", request.relayState())
        .formParam("SAMLResponse", signedResponse)
        .when().post("/auth/realms/saml-signed/broker/saml/signed-idp/acs")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();

    String localCode = queryValue(URI.create(acsResponse.getHeader("Location")), "code");
    if (localCode == null) {
      throw new AssertionError("signed SAML broker flow did not return local auth code");
    }

    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("grant_type", "authorization_code")
        .formParam("client_id", "saml-signed-web")
        .formParam("code", localCode)
        .formParam("redirect_uri", "http://client.example/saml/signed-callback")
        .formParam("code_verifier", codeVerifier)
        .when().post("/auth/realms/saml-signed/protocol/openid-connect/token")
        .then().statusCode(200)
        .body("access_token", notNullValue());
  }

  @Test
  void saml_broker_rejects_unsigned_response_when_certificate_configured() throws Exception {
    SigningMaterial signingMaterial = signingMaterial();
    realmWithSignedSamlProvider("saml-signed-invalid", "saml-signed-invalid-web", "signed-idp", signingMaterial);

    String codeChallenge = OidcGrantService.codeChallenge("saml-signed-invalid-verifier-123456789");
    BrokerRequest request = brokerRequest(
        "saml-signed-invalid",
        "signed-idp",
        "saml-signed-invalid-web",
        "http://client.example/saml/signed-invalid-callback",
        codeChallenge);

    String unsignedResponse = unsignedSamlResponse(
        "https://signed-idp.example.com/entity",
        request.spEntityId(),
        request.acsDestination(),
        request.authnRequestId(),
        "unsigned-user-123",
        "unsigneduser",
        "unsigneduser@example.com");

    given()
        .redirects().follow(false)
        .contentType("application/x-www-form-urlencoded")
        .formParam("RelayState", request.relayState())
        .formParam("SAMLResponse", unsignedResponse)
        .when().post("/auth/realms/saml-signed-invalid/broker/saml/signed-idp/acs")
        .then().statusCode(400)
        .body(containsString("invalid_request"));
  }

  private RealmSetup realmWithSignedSamlProvider(String realmName, String clientId, String alias, SigningMaterial signingMaterial) {
    Response realmResp = adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of("name", realmName, "displayName", realmName))
        .when().post("/admin/realms")
        .then().statusCode(anyOf(is(200), is(201)))
        .extract().response();
    String realmId = realmResp.jsonPath().getString("id");

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.ofEntries(
            new AbstractMap.SimpleEntry<>("clientId", clientId),
            new AbstractMap.SimpleEntry<>("protocol", "openid-connect"),
            new AbstractMap.SimpleEntry<>("publicClient", true),
            new AbstractMap.SimpleEntry<>("redirectUris", List.of(clientId.contains("invalid")
                ? "http://client.example/saml/signed-invalid-callback"
                : "http://client.example/saml/signed-callback")),
            new AbstractMap.SimpleEntry<>("grantTypes", List.of("authorization_code"))))
        .when().post("/admin/realms/" + realmId + "/clients")
        .then().statusCode(anyOf(is(200), is(201)));

    adminRequest()
        .contentType(ContentType.JSON)
        .body(Map.of(
            "alias", alias,
            "entityId", "https://signed-idp.example.com/entity",
            "ssoUrl", "https://signed-idp.example.com/sso",
            "x509Certificate", signingMaterial.certificatePem(),
            "enabled", true))
        .when().post("/admin/realms/" + realmId + "/brokering/saml")
        .then().statusCode(anyOf(is(200), is(201)));

    return new RealmSetup(realmId);
  }

  private BrokerRequest brokerRequest(
      String realmName,
      String alias,
      String clientId,
      String redirectUri,
      String codeChallenge) {
    Response brokerStart = given()
        .redirects().follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", redirectUri)
        .queryParam("scope", "openid profile email")
        .queryParam("state", "signed-state")
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .when().get("/auth/realms/" + realmName + "/broker/saml/" + alias + "/login")
        .then().statusCode(anyOf(is(302), is(303)))
        .extract().response();
    URI samlRedirect = URI.create(brokerStart.getHeader("Location"));
    String relayState = queryValue(samlRedirect, "RelayState");
    String samlRequest = queryValue(samlRedirect, "SAMLRequest");
    return new BrokerRequest(
        relayState,
        authnRequestId(samlRequest),
        authnRequestAcs(samlRequest),
        authnRequestIssuer(samlRequest));
  }

  private SigningMaterial signingMaterial() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    Instant now = Instant.now();
    X500Name subject = new X500Name("CN=Signed Test IdP");
    JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        subject,
        BigInteger.valueOf(System.nanoTime()).abs(),
        Date.from(now.minusSeconds(60)),
        Date.from(now.plusSeconds(86400)),
        subject,
        keyPair.getPublic());
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    X509Certificate certificate = new JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certBuilder.build(signer));
    String pem = "-----BEGIN CERTIFICATE-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(certificate.getEncoded())
        + "\n-----END CERTIFICATE-----";
    return new SigningMaterial(pem, keyPair.getPrivate());
  }

  private String signedSamlResponse(
      PrivateKey privateKey,
      String issuer,
      String audience,
      String destination,
      String inResponseTo,
      String subject,
      String username,
      String email) throws Exception {
    Document document = parseXml(unsignedSamlXml(issuer, audience, destination, inResponseTo, subject, username, email));
    Element response = document.getDocumentElement();
    response.setIdAttribute("ID", true);

    XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
    Reference reference = factory.newReference(
        "#" + response.getAttribute("ID"),
        factory.newDigestMethod(DigestMethod.SHA256, null),
        List.of(
            factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
            factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
        null,
        null);
    SignedInfo signedInfo = factory.newSignedInfo(
        factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
        factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
        List.of(reference));
    DOMSignContext signContext = new DOMSignContext(privateKey, response);
    signContext.setDefaultNamespacePrefix("ds");
    factory.newXMLSignature(signedInfo, null).sign(signContext);
    return Base64.getEncoder().encodeToString(toXml(document).getBytes(StandardCharsets.UTF_8));
  }

  private String unsignedSamlResponse(
      String issuer,
      String audience,
      String destination,
      String inResponseTo,
      String subject,
      String username,
      String email) {
    return Base64.getEncoder().encodeToString(unsignedSamlXml(
        issuer,
        audience,
        destination,
        inResponseTo,
        subject,
        username,
        email).getBytes(StandardCharsets.UTF_8));
  }

  private String unsignedSamlXml(
      String issuer,
      String audience,
      String destination,
      String inResponseTo,
      String subject,
      String username,
      String email) {
    String responseId = "_" + UUID.randomUUID();
    String assertionId = "_" + UUID.randomUUID();
    String notBefore = Instant.now().minusSeconds(30).toString();
    String notOnOrAfter = Instant.now().plusSeconds(300).toString();
    return """
        <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
            xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
            ID="%s"
            Destination="%s"
            InResponseTo="%s">
          <saml:Issuer>%s</saml:Issuer>
          <samlp:Status>
            <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
          </samlp:Status>
          <saml:Assertion ID="%s">
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
              <saml:Attribute Name="email_verified"><saml:AttributeValue>true</saml:AttributeValue></saml:Attribute>
            </saml:AttributeStatement>
          </saml:Assertion>
        </samlp:Response>
        """.formatted(
        responseId,
        destination,
        inResponseTo,
        issuer,
        assertionId,
        issuer,
        notBefore,
        notOnOrAfter,
        audience,
        subject,
        destination,
        notOnOrAfter,
        username,
        email);
  }

  private Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private String toXml(Document document) throws Exception {
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    StringWriter writer = new StringWriter();
    var transformer = factory.newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    transformer.transform(new DOMSource(document), new StreamResult(writer));
    return writer.toString();
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

  private record SigningMaterial(String certificatePem, PrivateKey privateKey) {}

  private record BrokerRequest(String relayState, String authnRequestId, String acsDestination, String spEntityId) {}

  private record RealmSetup(String realmId) {}
}
