package com.onesignal;

import android.content.res.Resources;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class KeyPinStore {
    private static final String[] certificates = {"onesignal.crt"};
    private static KeyPinStore instance = null;
    private SSLContext sslContext = SSLContext.getInstance("TLS");

    public static synchronized KeyPinStore getInstance() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        if (instance == null) {
            instance = new KeyPinStore();
        }
        return instance;
    }

    private KeyPinStore() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        for (int i = 0; i < certificates.length; i++) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            String file = "res/raw/" + certificates[i];
            InputStream caInput = this.getClass().getClassLoader().getResourceAsStream(file);
            Certificate ca;
            try {
                ca = cf.generateCertificate(caInput);
                System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
            } finally {
                caInput.close();
            }

            // Create a KeyStore containing our trusted CAs
            keyStore.setCertificateEntry("ca" + i, ca);
        }

        // Use custom trust manager to trusts the CAs in our KeyStore
        TrustManager[] trustManagers = {new CustomTrustManager(keyStore)};

        // Create an SSLContext that uses our TrustManager
        // SSLContext context = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);
    }

    public SSLContext getContext() {
        return sslContext;
    }
}
