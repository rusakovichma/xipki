/*
 *
 * Copyright (c) 2013 - 2018 Lijun Liao
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

package org.xipki.litecaclient.example;

import java.io.File;
import java.math.BigInteger;
import java.net.URL;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.xipki.litecaclient.KeyAndCert;
import org.xipki.litecaclient.PbmMacCmpCaClient;
import org.xipki.litecaclient.SdkUtil;
import org.xipki.litecaclient.TlsInit;

/**
 * TODO.
 * @author Lijun Liao
 */

public class PbmMacCmpCaClientExample extends CaClientExample {

  //private static final String URL_PREFIX = "http://localhost:8080";

  private static final String URL_PREFIX = "https://localhost:8443";

  private static final String CMP_URL = URL_PREFIX + "/cmp/myca";

  private static final String CACERT_URL = URL_PREFIX + "/cacert/myca";

  private static final String KEYCERT_DIR =  "target/tlskeys";

  private static final String RESPONDER_CERT_FILE = KEYCERT_DIR + "/tls-server.der";

  private static final String HASH_ALGO = "SHA256";

  private static final String CERT_PROFILE = "tls";

  private static final AtomicLong index = new AtomicLong(System.currentTimeMillis());

  private static final boolean profileAndKeyTypeInUri = true;

  public static void main(String[] args) {
    //System.setProperty("javax.net.debug", "all");

    if (!new File(KEYCERT_DIR).exists()) {
      System.err.println("Please call \"mvn generate-resources\" first.");
      return;
    }

    Security.addProvider(new BouncyCastleProvider());

    try {
      TlsInit.init();
      byte[] encodedCaCert =
          SdkUtil.send(new URL(CACERT_URL), "GET", null, null, "application/pkix-cert");
      TlsInit.shutdown();

      X509Certificate caCert = SdkUtil.parseCert(encodedCaCert);

      X509Certificate responderCert = SdkUtil.parseCert(new File(expandPath(RESPONDER_CERT_FILE)));

      X500Name requestorSubject = new X500Name("CN=PBMMAC");
      X500Name responderSubject = X500Name.getInstance(
          responderCert.getSubjectX500Principal().getEncoded());

      PbmMacCmpCaClient client = new PbmMacCmpCaClient(CMP_URL, caCert, requestorSubject,
          responderSubject, HASH_ALGO);

      // SHA1("requestor-mac1".getBytes())
      client.setKid(Hex.decode("466827c7757a70af71ca0338c01361aab2019dcf"));
      client.setPassword("123456".toCharArray());
      client.setRequestInterationCount(10240);
      client.setRequestMac(
          new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256, DERNull.INSTANCE));
      client.setRequestOwf(
          new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE));

      Set<ASN1ObjectIdentifier> owfOids = new HashSet<>();
      owfOids.add(NISTObjectIdentifiers.id_sha256);
      client.setTrustedOwfOids(owfOids);

      Set<ASN1ObjectIdentifier> macOids = new HashSet<>();
      macOids.add(PKCSObjectIdentifiers.id_hmacWithSHA256);
      client.setTrustedMacOids(macOids);

      client.init();

      // retrieve CA certificate
      printCert("===== CA Certificate =====", client.getCaCert());

      // Enroll certificate via CRMF - (CA generate keypair)
      KeyAndCert[] keyAndCerts =
          client.enrollCertViaCrmfCaGenKeypair(new String[] {CERT_PROFILE, CERT_PROFILE},
          new String[]{getSubject(), getSubject()}, profileAndKeyTypeInUri);
      for (int i = 0; i < keyAndCerts.length; i++) {
        printKeyAndCert("===== via CRMF (CMP, CA generate keypair) =====", keyAndCerts[i]);
      }

      // Enroll certificate via CSR - RSA
      MyKeypair kp = generateRsaKeypair();
      CertificationRequest csr = genCsr(kp, getSubject());
      X509Certificate cert = client.enrollCertViaCsr(CERT_PROFILE, csr, profileAndKeyTypeInUri);
      printCert("===== RSA via CSR (CMP) =====", cert);

      // Enroll certificate via CSR - EC
      kp = generateEcKeypair();
      csr = genCsr(kp, getSubject());
      cert = client.enrollCertViaCsr(CERT_PROFILE, csr, profileAndKeyTypeInUri);
      printCert("===== EC via CSR (CMP) =====", cert);

      // Enroll certificate via CSR - DSA
      kp = generateDsaKeypair();
      csr = genCsr(kp, getSubject());
      cert = client.enrollCertViaCsr(CERT_PROFILE, csr, profileAndKeyTypeInUri);
      printCert("===== DSA via CSR (CMP) =====", cert);

      // Enroll certificate via CRMF - RSA
      kp = generateRsaKeypair();
      cert = client.enrollCertViaCrmf(CERT_PROFILE, kp.getPrivate(), kp.getPublic(), getSubject(),
          profileAndKeyTypeInUri);
      printCert("===== RSA via CRMF (CMP) =====", cert);

      // Enroll certificate via CRMF - EC
      kp = generateEcKeypair();
      MyKeypair kp2 = generateEcKeypair();
      X509Certificate[] certs = client.enrollCertViaCrmf(new String[] {CERT_PROFILE, CERT_PROFILE},
          new PrivateKey[] {kp.getPrivate(), kp2.getPrivate()},
          new SubjectPublicKeyInfo[] {kp.getPublic(), kp2.getPublic()},
          new String[]{getSubject(), getSubject()}, profileAndKeyTypeInUri);
      for (int i = 0; i < certs.length; i++) {
        printCert("===== EC via CRMF (CMP) =====", certs[i]);
      }

      // Enroll certificate via CRMF - DSA
      kp = generateDsaKeypair();
      cert = client.enrollCertViaCrmf(CERT_PROFILE, kp.getPrivate(), kp.getPublic(), getSubject(),
          profileAndKeyTypeInUri);
      printCert("===== DSA via CRMF (CMP) =====", cert);

      BigInteger serialNumber = cert.getSerialNumber();
      // Suspend certificate
      boolean flag = client.revokeCert(serialNumber, CRLReason.lookup(CRLReason.certificateHold));
      if (flag) {
        System.out.println("(CMP) suspended certificate");
      } else {
        System.err.println("(CMP) suspending certificate failed");
      }

      // Unsuspend certificate
      flag = client.revokeCert(serialNumber, CRLReason.lookup(CRLReason.removeFromCRL));
      if (flag) {
        System.out.println("(CMP) unsuspended certificate");
      } else {
        System.err.println("(CMP) unsuspending certificate failed");
      }

      // Revoke certificate
      flag = client.revokeCert(serialNumber, CRLReason.lookup(CRLReason.keyCompromise));
      if (flag) {
        System.out.println("(CMP) revoked certificate");
      } else {
        System.err.println("(CMP) revoking certificate failed");
      }

      client.shutdown();
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
  }

  private static String getSubject() {
    return "CN=CMP-" + index.incrementAndGet() + ".xipki.org,O=xipki,C=DE";
  }

}
