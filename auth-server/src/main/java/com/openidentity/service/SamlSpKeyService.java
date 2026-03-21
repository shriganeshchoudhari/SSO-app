package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ApplicationScoped
public class SamlSpKeyService {
  @Inject JwtKeyService jwtKeyService;

  public String signingKeyDescriptorXml() {
    RSAPublicKey publicKey = (RSAPublicKey) jwtKeyService.getPublicKey();
    return """
        <KeyDescriptor use="signing">
          <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <ds:KeyValue>
              <ds:RSAKeyValue>
                <ds:Modulus>%s</ds:Modulus>
                <ds:Exponent>%s</ds:Exponent>
              </ds:RSAKeyValue>
            </ds:KeyValue>
          </ds:KeyInfo>
        </KeyDescriptor>
        """.formatted(
        base64(publicKey.getModulus()),
        base64(publicKey.getPublicExponent()));
  }

  public String signAuthnRequest(String xml) {
    return signXml(xml, "AuthnRequest");
  }

  public String signXml(String xml, String artifactName) {
    try {
      Document document = parse(xml);
      Element root = document.getDocumentElement();
      root.setIdAttribute("ID", true);

      XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
      Reference reference = factory.newReference(
          "#" + root.getAttribute("ID"),
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
      DOMSignContext signContext = new DOMSignContext(jwtKeyService.getPrivateKey(), root);
      signContext.setDefaultNamespacePrefix("ds");
      factory.newXMLSignature(signedInfo, null).sign(signContext);
      return toXml(document);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to sign SAML " + artifactName, e);
    }
  }

  private Document parse(String xml) throws Exception {
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

  private String base64(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      bytes = trimmed;
    }
    return Base64.getEncoder().encodeToString(bytes);
  }
}
