package com.dhpcs.liquidity;

import android.content.Context;
import android.util.Base64;

import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.openssl.PEMParser;
import org.spongycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ServerTrust {

    private static volatile ServerTrust instance;

    public static ServerTrust getInstance(Context context) {
        if (instance == null) {
            synchronized (ServerTrust.class) {
                if (instance == null) {
                    instance = new ServerTrust(context);
                }
            }
        }
        return instance;
    }

    private final Map<String, PublicKey> keys = new HashMap<>();

    private ServerTrust(Context context) {
        readPublicKey(
                "liquidity.dhpcs.com",
                context,
                R.raw.liquidity_dhpcs_com
        );
    }

    public HostnameVerifier getHostnameVerifier() {
        return new HostnameVerifier() {

            @Override
            public boolean verify(String hostname, SSLSession session) {
                PublicKey expectedPublicKey = keys.get(hostname);
                if (expectedPublicKey == null) {
                    return false;
                } else {
                    try {
                        PublicKey actualPublicKey =
                                session.getPeerCertificates()[0].getPublicKey();
                        return Arrays.equals(
                                actualPublicKey.getEncoded(),
                                expectedPublicKey.getEncoded()
                        );
                    } catch (SSLPeerUnverifiedException e) {
                        return false;
                    }
                }
            }

        };
    }

    public TrustManager[] getTrustManagers() {
        return new TrustManager[]{
                new X509TrustManager() {

                    @Override
                    public void checkClientTrusted(
                            X509Certificate[] chain, String authType) throws CertificateException {
                        checkTrusted(chain);
                    }

                    public void checkServerTrusted(
                            X509Certificate[] chain, String authType) throws CertificateException {
                        checkTrusted(chain);
                    }

                    private void checkTrusted(X509Certificate[] chain) throws CertificateException {
                        if (chain == null || chain.length == 0) {
                            throw new IllegalArgumentException("chain is null or empty");
                        }

                        PublicKey publicKey = chain[0].getPublicKey();
                        for (String hostname : keys.keySet()) {
                            PublicKey trustedPublicKey = keys.get(hostname);
                            if (Arrays.equals(
                                    publicKey.getEncoded(),
                                    trustedPublicKey.getEncoded())
                                    ) {
                                return;
                            }
                        }
                        throw new CertificateException(
                                "Unknown public key: "
                                        + Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT)
                        );
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                }
        };
    }

    private void readPublicKey(String hostname, Context context, int resId) {
        try {
            PublicKey publicKey = new JcaPEMKeyConverter().getPublicKey(
                    (SubjectPublicKeyInfo) new PEMParser(
                            new InputStreamReader(context.getResources().openRawResource(resId))
                    ).readObject()
            );
            keys.put(hostname, publicKey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
