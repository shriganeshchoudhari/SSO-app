package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@ApplicationScoped
public class SamlSignatureValidationService {
  private static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";

  public void validate(Document document, String certificatePem) {
    if (certificatePem == null || certificatePem.isBlank()) {
      return;
    }
    try {
      X509Certificate certificate = parseCertificate(certificatePem);
      registerIdAttributes(document);
      NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
      if (signatures.getLength() == 0) {
        throw new IllegalArgumentException("Signed SAML response is missing XML signature");
      }
      XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
      for (int i = 0; i < signatures.getLength(); i++) {
        DOMValidateContext validateContext = new DOMValidateContext(certificate.getPublicKey(), signatures.item(i));
        validateContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        XMLSignature signature = factory.unmarshalXMLSignature(validateContext);
        if (signature.validate(validateContext)) {
          return;
        }
      }
      throw new IllegalArgumentException("SAML signature validation failed");
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("SAML signature validation failed", e);
    }
  }

  private X509Certificate parseCertificate(String certificatePem) throws Exception {
    String normalized = certificatePem
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replaceAll("\\s+", "");
    byte[] bytes = Base64.getDecoder().decode(normalized);
    return (X509Certificate) CertificateFactory.getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(bytes));
  }

  private void registerIdAttributes(Document document) {
    Element response = document.getDocumentElement();
    if (response != null && response.hasAttribute("ID")) {
      response.setIdAttribute("ID", true);
    }
    NodeList assertions = document.getElementsByTagNameNS(ASSERTION_NS, "Assertion");
    for (int i = 0; i < assertions.getLength(); i++) {
      Element assertion = (Element) assertions.item(i);
      if (assertion.hasAttribute("ID")) {
        assertion.setIdAttribute("ID", true);
      }
    }
  }
}
