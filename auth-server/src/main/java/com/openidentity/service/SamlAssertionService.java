package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@ApplicationScoped
public class SamlAssertionService {
  private static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
  private static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
  private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);

  @Inject SamlSignatureValidationService samlSignatureValidationService;

  public record SamlProfile(String subject, String username, String email, Boolean emailVerified) {}

  public record SamlValidationContext(
      String expectedIssuer,
      String expectedAudience,
      String expectedDestination,
      String expectedInResponseTo,
      String x509CertificatePem) {}

  public SamlProfile parseResponse(String samlResponse, SamlValidationContext validationContext) {
    if (samlResponse == null || samlResponse.isBlank()) {
      throw new IllegalArgumentException("SAMLResponse is required");
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(samlResponse);
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Document document = factory.newDocumentBuilder()
          .parse(new ByteArrayInputStream(decoded));

      validateResponse(document, validationContext);

      String subject = firstText(document, ASSERTION_NS, "NameID");
      String username = attributeValue(document, "username");
      String email = attributeValue(document, "email");
      Boolean emailVerified = booleanValue(attributeValue(document, "email_verified"));
      if (subject == null || subject.isBlank()) {
        throw new IllegalArgumentException("SAML response is missing NameID");
      }
      if (username == null || username.isBlank()) {
        username = email != null && !email.isBlank() ? email : subject;
      }
      return new SamlProfile(subject, username, email, emailVerified);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to parse SAML response", e);
    }
  }

  private void validateResponse(Document document, SamlValidationContext validationContext) {
    Element response = document.getDocumentElement();
    if (response == null || !PROTOCOL_NS.equals(response.getNamespaceURI()) || !"Response".equals(response.getLocalName())) {
      throw new IllegalArgumentException("SAML response root element is invalid");
    }
    requireSuccessStatus(document);
    samlSignatureValidationService.validate(document, validationContext.x509CertificatePem());
    requireAttributeEquals(response, "Destination", validationContext.expectedDestination(), true);
    requireAttributeEquals(response, "InResponseTo", validationContext.expectedInResponseTo(), true);
    requireIssuer(document, validationContext.expectedIssuer());
    requireAudience(document, validationContext.expectedAudience());
    validateConditions(document);
    validateSubjectConfirmation(document, validationContext.expectedDestination());
  }

  private void requireSuccessStatus(Document document) {
    NodeList statusCodes = document.getElementsByTagNameNS(PROTOCOL_NS, "StatusCode");
    if (statusCodes.getLength() == 0) {
      return;
    }
    Element statusCode = (Element) statusCodes.item(0);
    String value = normalize(statusCode.getAttribute("Value"));
    if (!"urn:oasis:names:tc:SAML:2.0:status:Success".equals(value)) {
      throw new IllegalArgumentException("SAML response status is not success");
    }
  }

  private void requireIssuer(Document document, String expectedIssuer) {
    NodeList issuers = document.getElementsByTagNameNS(ASSERTION_NS, "Issuer");
    boolean found = false;
    for (int i = 0; i < issuers.getLength(); i++) {
      String issuer = normalize(issuers.item(i).getTextContent());
      if (issuer == null) {
        continue;
      }
      found = true;
      if (expectedIssuer != null && !expectedIssuer.equals(issuer)) {
        throw new IllegalArgumentException("SAML issuer does not match provider");
      }
    }
    if (!found) {
      throw new IllegalArgumentException("SAML response is missing issuer");
    }
  }

  private void requireAudience(Document document, String expectedAudience) {
    if (expectedAudience == null || expectedAudience.isBlank()) {
      return;
    }
    NodeList audiences = document.getElementsByTagNameNS(ASSERTION_NS, "Audience");
    if (audiences.getLength() == 0) {
      throw new IllegalArgumentException("SAML response is missing audience restriction");
    }
    for (int i = 0; i < audiences.getLength(); i++) {
      String audience = normalize(audiences.item(i).getTextContent());
      if (expectedAudience.equals(audience)) {
        return;
      }
    }
    throw new IllegalArgumentException("SAML audience does not match service provider");
  }

  private void validateConditions(Document document) {
    NodeList conditionsList = document.getElementsByTagNameNS(ASSERTION_NS, "Conditions");
    for (int i = 0; i < conditionsList.getLength(); i++) {
      Element conditions = (Element) conditionsList.item(i);
      validateInstantFloor(normalize(conditions.getAttribute("NotBefore")), "SAML assertion is not yet valid");
      validateInstantCeiling(normalize(conditions.getAttribute("NotOnOrAfter")), "SAML assertion has expired");
    }
  }

  private void validateSubjectConfirmation(Document document, String expectedRecipient) {
    NodeList confirmationDataList = document.getElementsByTagNameNS(ASSERTION_NS, "SubjectConfirmationData");
    if (confirmationDataList.getLength() == 0) {
      throw new IllegalArgumentException("SAML response is missing subject confirmation data");
    }
    for (int i = 0; i < confirmationDataList.getLength(); i++) {
      Element confirmationData = (Element) confirmationDataList.item(i);
      requireAttributeEquals(confirmationData, "Recipient", expectedRecipient, true);
      validateInstantCeiling(normalize(confirmationData.getAttribute("NotOnOrAfter")), "SAML subject confirmation has expired");
      return;
    }
  }

  private void requireAttributeEquals(Element element, String attributeName, String expectedValue, boolean required) {
    String actual = normalize(element.getAttribute(attributeName));
    if (actual == null) {
      if (required) {
        throw new IllegalArgumentException("SAML response is missing " + attributeName);
      }
      return;
    }
    if (expectedValue != null && !expectedValue.equals(actual)) {
      throw new IllegalArgumentException("SAML " + attributeName + " does not match expected request");
    }
  }

  private void validateInstantFloor(String value, String message) {
    if (value == null) {
      return;
    }
    Instant instant = parseInstant(value);
    if (instant.isAfter(Instant.now().plus(CLOCK_SKEW))) {
      throw new IllegalArgumentException(message);
    }
  }

  private void validateInstantCeiling(String value, String message) {
    if (value == null) {
      return;
    }
    Instant instant = parseInstant(value);
    if (!instant.isAfter(Instant.now().minus(CLOCK_SKEW))) {
      throw new IllegalArgumentException(message);
    }
  }

  private Instant parseInstant(String value) {
    try {
      return Instant.parse(value);
    } catch (Exception ignored) {
      return OffsetDateTime.parse(value).toInstant();
    }
  }

  private String firstText(Document document, String namespace, String localName) {
    NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
    if (nodes.getLength() == 0) {
      return null;
    }
    String value = nodes.item(0).getTextContent();
    return normalize(value);
  }

  private String attributeValue(Document document, String attributeName) {
    NodeList attributes = document.getElementsByTagNameNS(ASSERTION_NS, "Attribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Element attribute = (Element) attributes.item(i);
      if (attributeName.equals(attribute.getAttribute("Name"))) {
        NodeList values = attribute.getElementsByTagNameNS(ASSERTION_NS, "AttributeValue");
        if (values.getLength() > 0) {
          return normalize(values.item(0).getTextContent());
        }
      }
    }
    return null;
  }

  private Boolean booleanValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Boolean.parseBoolean(value);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
