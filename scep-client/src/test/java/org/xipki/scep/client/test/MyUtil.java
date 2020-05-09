/*
 *
 * Copyright (c) 2013 - 2020 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.scep.client.test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.xipki.scep.serveremulator.CaEmulator;
import org.xipki.scep.util.ScepUtil;
import org.xipki.security.HashAlgo;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;
import org.xipki.util.CollectionUtil;
import org.xipki.util.StringUtil;

/**
 * Utility class.
 *
 * @author Lijun Liao
 */

public class MyUtil {

  private static final long MIN_IN_MS = 60L * 1000;

  private static final long DAY_IN_MS = 24L * 60 * MIN_IN_MS;

  private static final AlgorithmIdentifier ALGID_RSA =
      new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);

  public static X509Cert issueSubCaCert(PrivateKey rcaKey, X500Name issuer,
      SubjectPublicKeyInfo pubKeyInfo, X500Name subject, BigInteger serialNumber,
      Date startTime) throws CertIOException, OperatorCreationException {
    Date notAfter = new Date(startTime.getTime() + CaEmulator.DAY_IN_MS * 3650);
    X509v3CertificateBuilder certGenerator = new X509v3CertificateBuilder(issuer, serialNumber,
        startTime, notAfter, subject, pubKeyInfo);
    X509KeyUsage ku = new X509KeyUsage(X509KeyUsage.keyCertSign | X509KeyUsage.cRLSign);
    certGenerator.addExtension(Extension.keyUsage, true, ku);
    BasicConstraints bc = new BasicConstraints(0);
    certGenerator.addExtension(Extension.basicConstraints, true, bc);

    String signatureAlgorithm = ScepUtil.getSignatureAlgorithm(rcaKey, HashAlgo.SHA256);
    ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(rcaKey);
    return new X509Cert(certGenerator.build(contentSigner));
  } // method issueSubCaCert

  public static PKCS10CertificationRequest generateRequest(PrivateKey privatekey,
      SubjectPublicKeyInfo subjectPublicKeyInfo, X500Name subjectDn,
      String challengePassword, List<Extension> extensions)
      throws OperatorCreationException {
    Args.notNull(privatekey, "privatekey");
    Args.notNull(subjectPublicKeyInfo, "subjectPublicKeyInfo");
    Args.notNull(subjectDn, "subjectDn");

    Map<ASN1ObjectIdentifier, ASN1Encodable> attributes =
        new HashMap<ASN1ObjectIdentifier, ASN1Encodable>();

    if (StringUtil.isNotBlank(challengePassword)) {
      DERPrintableString asn1Pwd = new DERPrintableString(challengePassword);
      attributes.put(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, asn1Pwd);
    }

    if (CollectionUtil.isNotEmpty(extensions)) {
      Extensions asn1Extensions = new Extensions(extensions.toArray(new Extension[0]));
      attributes.put(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, asn1Extensions);
    }

    PKCS10CertificationRequestBuilder csrBuilder =
        new PKCS10CertificationRequestBuilder(subjectDn, subjectPublicKeyInfo);

    if (attributes != null) {
      for (ASN1ObjectIdentifier attrType : attributes.keySet()) {
        csrBuilder.addAttribute(attrType, attributes.get(attrType));
      }
    }

    ContentSigner contentSigner = new JcaContentSignerBuilder(
        ScepUtil.getSignatureAlgorithm(privatekey, HashAlgo.SHA1)).build(privatekey);
    return csrBuilder.build(contentSigner);
  } // method generateRequest

  public static X509Cert generateSelfsignedCert(CertificationRequest csr,
      PrivateKey identityKey) throws CertificateException {
    Args.notNull(csr, "csr");
    return generateSelfsignedCert(csr.getCertificationRequestInfo().getSubject(),
        csr.getCertificationRequestInfo().getSubjectPublicKeyInfo(), identityKey);
  }

  public static X509Cert generateSelfsignedCert(X500Name subjectDn,
      SubjectPublicKeyInfo pubKeyInfo, PrivateKey identityKey) throws CertificateException {
    Args.notNull(subjectDn, "subjectDn");
    Args.notNull(pubKeyInfo, "pubKeyInfo");
    Args.notNull(identityKey, "identityKey");

    Date notBefore = new Date(System.currentTimeMillis() - 5 * MIN_IN_MS);
    Date notAfter = new Date(notBefore.getTime() + 30 * DAY_IN_MS);

    X509v3CertificateBuilder certGenerator = new X509v3CertificateBuilder(subjectDn,
        BigInteger.ONE, notBefore, notAfter, subjectDn, pubKeyInfo);

    X509KeyUsage ku = new X509KeyUsage(X509KeyUsage.digitalSignature
        | X509KeyUsage.dataEncipherment | X509KeyUsage.keyAgreement | X509KeyUsage.keyEncipherment);
    try {
      certGenerator.addExtension(Extension.keyUsage, true, ku);
    } catch (CertIOException ex) {
      throw new CertificateException(
          "could not generate self-signed certificate: " + ex.getMessage(), ex);
    }

    String sigAlgorithm = ScepUtil.getSignatureAlgorithm(identityKey, HashAlgo.SHA1);
    ContentSigner contentSigner;
    try {
      contentSigner = new JcaContentSignerBuilder(sigAlgorithm).build(identityKey);
    } catch (OperatorCreationException ex) {
      throw new CertificateException("error while creating signer", ex);
    }

    return new X509Cert(certGenerator.build(contentSigner));
  } // method generateSelfsignedCert

  public static SubjectPublicKeyInfo createSubjectPublicKeyInfo(PublicKey publicKey)
      throws IOException {
    Args.notNull(publicKey, "publicKey");
    if (publicKey instanceof java.security.interfaces.RSAPublicKey) {
      java.security.interfaces.RSAPublicKey rsaPubKey =
          (java.security.interfaces.RSAPublicKey) publicKey;
      return new SubjectPublicKeyInfo(ALGID_RSA,
          new RSAPublicKey(rsaPubKey.getModulus(), rsaPubKey.getPublicExponent()));
    } else {
      throw new IllegalArgumentException("unsupported public key " + publicKey);
    }
  } // method createSubjectPublicKeyInfo

}
