package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@ApplicationScoped
public class SamlLogoutValidationService {
  private static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
  private static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
  private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);

  @Inject SamlSignatureValidationService samlSignatureValidationService;

  public record SamlLogoutValidationContext(
      String expectedIssuer,
      String expectedDestination,
      String expectedInResponseTo,
      String x509CertificatePem) {}

  public record SamlLogoutRequestValidationContext(
      String expectedIssuer,
      String expectedDestination,
      String x509CertificatePem) {}

  public record SamlLogoutRequest(
      String requestId,
      String subject,
      String sessionIndex) {}

  public void validateLogoutResponse(String samlResponse, SamlLogoutValidationContext validationContext) {
    if (samlResponse == null || samlResponse.isBlank()) {
      throw new IllegalArgumentException("SAMLResponse is required");
    }
    try {
      Document document = parseBase64Xml(samlResponse, "SAML logout response");
      validate(document, validationContext);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to validate SAML logout response", e);
    }
  }

  public SamlLogoutRequest validateLogoutRequest(String samlRequest, SamlLogoutRequestValidationContext validationContext) {
    if (samlRequest == null || samlRequest.isBlank()) {
      throw new IllegalArgumentException("SAMLRequest is required");
    }
    try {
      Document document = parseBase64Xml(samlRequest, "SAML logout request");
      return validateLogoutRequest(document, validationContext);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to validate SAML logout request", e);
    }
  }

  private void validate(Document document, SamlLogoutValidationContext validationContext) {
    Element response = document.getDocumentElement();
    if (response == null || !PROTOCOL_NS.equals(response.getNamespaceURI()) || !"LogoutResponse".equals(response.getLocalName())) {
      throw new IllegalArgumentException("SAML logout response root element is invalid");
    }
    samlSignatureValidationService.validate(document, validationContext.x509CertificatePem());
    requireAttributeEquals(response, "Destination", validationContext.expectedDestination());
    requireAttributeEquals(response, "InResponseTo", validationContext.expectedInResponseTo());
    requireIssuer(document, validationContext.expectedIssuer());
    requireSuccessStatus(document);
    validateNotOnOrAfter(response);
  }

  private SamlLogoutRequest validateLogoutRequest(Document document, SamlLogoutRequestValidationContext validationContext) {
    Element request = document.getDocumentElement();
    if (request == null || !PROTOCOL_NS.equals(request.getNamespaceURI()) || !"LogoutRequest".equals(request.getLocalName())) {
      throw new IllegalArgumentException("SAML logout request root element is invalid");
    }
    samlSignatureValidationService.validate(document, validationContext.x509CertificatePem());
    requireAttributeEquals(request, "Destination", validationContext.expectedDestination());
    requireIssuer(document, validationContext.expectedIssuer());
    validateNotOnOrAfter(request);
    String requestId = normalize(request.getAttribute("ID"));
    if (requestId == null) {
      throw new IllegalArgumentException("SAML logout request is missing ID");
    }
    String subject = requireSubject(document);
    String sessionIndex = optionalFirstText(document, PROTOCOL_NS, "SessionIndex");
    return new SamlLogoutRequest(requestId, subject, sessionIndex);
  }

  private void requireIssuer(Document document, String expectedIssuer) {
    NodeList issuers = document.getElementsByTagNameNS(ASSERTION_NS, "Issuer");
    if (issuers.getLength() == 0) {
      throw new IllegalArgumentException("SAML logout response is missing issuer");
    }
    String issuer = normalize(issuers.item(0).getTextContent());
    if (issuer == null || !issuer.equals(expectedIssuer)) {
      throw new IllegalArgumentException("SAML logout issuer does not match provider");
    }
  }

  private void requireSuccessStatus(Document document) {
    NodeList statusCodes = document.getElementsByTagNameNS(PROTOCOL_NS, "StatusCode");
    if (statusCodes.getLength() == 0) {
      return;
    }
    Element statusCode = (Element) statusCodes.item(0);
    String value = normalize(statusCode.getAttribute("Value"));
    if (!"urn:oasis:names:tc:SAML:2.0:status:Success".equals(value)) {
      throw new IllegalArgumentException("SAML logout response status is not success");
    }
  }

  private String requireSubject(Document document) {
    NodeList subjects = document.getElementsByTagNameNS(ASSERTION_NS, "NameID");
    if (subjects.getLength() == 0) {
      throw new IllegalArgumentException("SAML logout request is missing subject");
    }
    String subject = normalize(subjects.item(0).getTextContent());
    if (subject == null) {
      throw new IllegalArgumentException("SAML logout request is missing subject");
    }
    return subject;
  }

  private String optionalFirstText(Document document, String namespace, String localName) {
    NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
    if (nodes.getLength() == 0) {
      return null;
    }
    return normalize(nodes.item(0).getTextContent());
  }

  private void validateNotOnOrAfter(Element response) {
    String notOnOrAfter = normalize(response.getAttribute("NotOnOrAfter"));
    if (notOnOrAfter == null) {
      return;
    }
    Instant instant = Instant.parse(notOnOrAfter);
    if (!instant.isAfter(Instant.now().minus(CLOCK_SKEW))) {
      throw new IllegalArgumentException("SAML logout response has expired");
    }
  }

  private void requireAttributeEquals(Element element, String attributeName, String expectedValue) {
    String actual = normalize(element.getAttribute(attributeName));
    if (actual == null || !actual.equals(expectedValue)) {
      throw new IllegalArgumentException("SAML logout " + attributeName + " does not match expected request");
    }
  }

  private Document parseBase64Xml(String encodedXml, String label) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    try {
      return factory.newDocumentBuilder()
          .parse(new ByteArrayInputStream(Base64.getDecoder().decode(encodedXml.getBytes(StandardCharsets.UTF_8))));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(label + " is not valid Base64", e);
    }
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
