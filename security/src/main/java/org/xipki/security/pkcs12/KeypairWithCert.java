/*
 *
 * Copyright (c) 2013 - 2019 Lijun Liao
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

package org.xipki.security.pkcs12;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.bouncycastle.jcajce.interfaces.EdDSAKey;
import org.bouncycastle.jcajce.interfaces.XDHKey;
import org.xipki.security.XiSecurityException;
import org.xipki.security.util.KeyUtil;
import org.xipki.security.util.X509Util;
import org.xipki.util.Args;

/**
 * Keypair with certificate.
 *
 * @author Lijun Liao
 *
 */
public class KeypairWithCert {

  private final PrivateKey key;

  private final PublicKey publicKey;

  private final X509Certificate[] certificateChain;

  public KeypairWithCert(PrivateKey key, X509Certificate[] certificateChain) {
    this.key = Args.notNull(key, "key");
    this.certificateChain = Args.notNull(certificateChain, "certificateChain");
    Args.min(certificateChain.length, "certificateChain.length", 1);
    this.publicKey = certificateChain[0].getPublicKey();
  }

  public static KeypairWithCert fromKeystore(String keystoreType, InputStream keystoreStream,
      char[] keystorePassword, String keyname, char[] keyPassword, X509Certificate cert)
          throws XiSecurityException {
    return fromKeystore(keystoreType, keystoreStream, keystorePassword, keyname, keyPassword,
        cert == null ? null : new X509Certificate[] {cert});
  }

  public static KeypairWithCert fromKeystore(String keystoreType, InputStream keystoreStream,
      char[] keystorePassword, String keyname, char[] keyPassword, X509Certificate[] certchain)
          throws XiSecurityException {
    if (!("PKCS12".equalsIgnoreCase(keystoreType) || "JKS".equalsIgnoreCase(keystoreType))) {
      throw new IllegalArgumentException("unsupported keystore type: " + keystoreType);
    }

    Args.notNull(keystoreStream, "keystoreStream");
    Args.notNull(keystorePassword, "keystorePassword");
    Args.notNull(keyPassword, "keyPassword");

    KeyStore keystore;
    try {
      keystore = KeyUtil.getKeyStore(keystoreType);
    } catch (KeyStoreException ex) {
      throw new XiSecurityException(ex.getMessage(), ex);
    }

    try {
      keystore.load(keystoreStream, keystorePassword);
      return fromKeystore(keystore, keyname, keyPassword, certchain);
    } catch (NoSuchAlgorithmException | ClassCastException | CertificateException
        | IOException ex) {
      throw new XiSecurityException(ex.getMessage(), ex);
    } finally {
      try {
        keystoreStream.close();
      } catch (IOException ex) {
        // CHECKSTYLE:SKIP
      }
    }
  }

  public static KeypairWithCert fromKeystore(KeyStore keystore,
      String keyname, char[] keyPassword, X509Certificate[] certchain)
          throws XiSecurityException {
    Args.notNull(keyPassword, "keyPassword");

    try {

      String tmpKeyname = keyname;
      if (tmpKeyname == null) {
        Enumeration<String> aliases = keystore.aliases();
        while (aliases.hasMoreElements()) {
          String alias = aliases.nextElement();
          if (keystore.isKeyEntry(alias)) {
            tmpKeyname = alias;
            break;
          }
        }
      } else {
        if (!keystore.isKeyEntry(tmpKeyname)) {
          throw new XiSecurityException("unknown key named " + tmpKeyname);
        }
      }

      PrivateKey key = (PrivateKey) keystore.getKey(tmpKeyname, keyPassword);

      if (!(key instanceof RSAPrivateKey || key instanceof DSAPrivateKey
          || key instanceof ECPrivateKey
          || key instanceof EdDSAKey || key instanceof XDHKey)) {
        throw new XiSecurityException("unsupported key " + key.getClass().getName());
      }

      Set<Certificate> caCerts = new HashSet<>();

      X509Certificate cert;
      if (certchain != null && certchain.length > 0) {
        cert = certchain[0];
        final int n = certchain.length;
        if (n > 1) {
          for (int i = 1; i < n; i++) {
            caCerts.add(certchain[i]);
          }
        }
      } else {
        cert = (X509Certificate) keystore.getCertificate(tmpKeyname);
      }

      Certificate[] certsInKeystore = keystore.getCertificateChain(tmpKeyname);
      if (certsInKeystore.length > 1) {
        for (int i = 1; i < certsInKeystore.length; i++) {
          caCerts.add(certsInKeystore[i]);
        }
      }

      X509Certificate[] certificateChain = X509Util.buildCertPath(cert, caCerts);

      return new KeypairWithCert(key, certificateChain);
    } catch (KeyStoreException | NoSuchAlgorithmException
        | UnrecoverableKeyException | ClassCastException | CertPathBuilderException ex) {
      throw new XiSecurityException(ex.getMessage(), ex);
    }
  }

  public PrivateKey getKey() {
    return key;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public X509Certificate[] getCertificateChain() {
    return certificateChain;
  }

}
