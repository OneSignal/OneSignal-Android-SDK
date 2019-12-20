package com.onesignal;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * A custom X509TrustManager implementation that trusts a specified server certificate in addition
 * to those that are in the system TrustStore.
 * Also handles an out-of-order certificate chain, as is often produced by Apache's mod_ssl
 */
public class CustomTrustManager implements X509TrustManager {

  private final TrustManager[] originalTrustManagers;
  private final KeyStore trustStore;

  /**
   * @param trustStore A KeyStore containing the server certificate that should be trusted
   * @throws NoSuchAlgorithmException
   * @throws KeyStoreException
   */
  public CustomTrustManager(KeyStore trustStore) throws NoSuchAlgorithmException, KeyStoreException {
    this.trustStore = trustStore;

    final TrustManagerFactory originalTrustManagerFactory = TrustManagerFactory.getInstance("X509");
    originalTrustManagerFactory.init(trustStore);

    originalTrustManagers = originalTrustManagerFactory.getTrustManagers();
  }

  /**
   * No-op. Never invoked by client, only used in server-side implementations
   * @return
   */
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }

  /**
   * No-op. Never invoked by client, only used in server-side implementations
   * @return
   */
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
  }


  /**
   * Given the partial or complete certificate chain provided by the peer,
   * build a certificate path to a trusted root and return if it can be validated and is trusted
   * for client SSL authentication based on the authentication type. The authentication type is
   * determined by the actual certificate used. For instance, if RSAPublicKey is used, the authType should be "RSA".
   * Checking is case-sensitive.
   * Defers to the default trust manager first, checks the cert supplied in the ctor if that fails.
   * @param chain the server's certificate chain
   * @param authType the authentication type based on the client certificate
   * @throws java.security.cert.CertificateException
   */
  public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
    try {
      for (TrustManager originalTrustManager : originalTrustManagers) {
        ((X509TrustManager) originalTrustManager).checkServerTrusted(chain, authType);
      }
    } catch(CertificateException originalException) {
      try {
        // Ordering issue?
        X509Certificate[] reorderedChain = reorderCertificateChain(chain);
        if (! Arrays.equals(chain, reorderedChain)) {
          checkServerTrusted(reorderedChain, authType);
          return;
        }
        for (int i = 0; i < chain.length; i++) {
          if (validateCert(reorderedChain[i])) {
            return;
          }
        }
        throw originalException;
      } catch(Exception ex) {
        ex.printStackTrace();
        throw originalException;
      }
    }

  }

  /**
   * Checks if we have added the certificate in the trustStore, if that's the case we trust the certificate
   * @param x509Certificate the certificate to check
   * @return true if we know the certificate, false otherwise
   * @throws KeyStoreException on problems accessing the key store
   */
  private boolean validateCert(final X509Certificate x509Certificate) throws KeyStoreException {
    return trustStore.getCertificateAlias(x509Certificate) != null;
  }

  /**
   * Puts the certificate chain in the proper order, to deal with out-of-order
   * certificate chains as are sometimes produced by Apache's mod_ssl
   * @param chain the certificate chain, possibly with bad ordering
   * @return the re-ordered certificate chain
   */
  private X509Certificate[] reorderCertificateChain(X509Certificate[] chain) {

    X509Certificate[] reorderedChain = new X509Certificate[chain.length];
    List<X509Certificate> certificates = Arrays.asList(chain);

    int position = chain.length - 1;
    X509Certificate rootCert = findRootCert(certificates);
    reorderedChain[position] = rootCert;

    X509Certificate cert = rootCert;
    while((cert = findSignedCert(cert, certificates)) != null && position > 0) {
      reorderedChain[--position] = cert;
    }

    return reorderedChain;
  }

  /**
   * A helper method for certificate re-ordering.
   * Finds the root certificate in a possibly out-of-order certificate chain.
   * @param certificates the certificate change, possibly out-of-order
   * @return the root certificate, if any, that was found in the list of certificates
   */
  private X509Certificate findRootCert(List<X509Certificate> certificates) {
    X509Certificate rootCert = null;

    for(X509Certificate cert : certificates) {
      X509Certificate signer = findSigner(cert, certificates);
      if(signer == null || signer.equals(cert)) { // no signer present, or self-signed
        rootCert = cert;
        break;
      }
    }

    return rootCert;
  }

  /**
   * A helper method for certificate re-ordering.
   * Finds the first certificate in the list of certificates that is signed by the sigingCert.
   */
  private X509Certificate findSignedCert(X509Certificate signingCert, List<X509Certificate> certificates) {
    X509Certificate signed = null;

    for(X509Certificate cert : certificates) {
      Principal signingCertSubjectDN = signingCert.getSubjectDN();
      Principal certIssuerDN = cert.getIssuerDN();
      if(certIssuerDN.equals(signingCertSubjectDN) && !cert.equals(signingCert)) {
        signed = cert;
        break;
      }
    }

    return signed;
  }

  /**
   * A helper method for certificate re-ordering.
   * Finds the certificate in the list of certificates that signed the signedCert.
   */
  private X509Certificate findSigner(X509Certificate signedCert, List<X509Certificate> certificates) {
    X509Certificate signer = null;

    for(X509Certificate cert : certificates) {
      Principal certSubjectDN = cert.getSubjectDN();
      Principal issuerDN = signedCert.getIssuerDN();
      if(certSubjectDN.equals(issuerDN)) {
        signer = cert;
        break;
      }
    }

    return signer;
  }
}
