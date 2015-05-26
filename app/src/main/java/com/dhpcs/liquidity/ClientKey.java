package com.dhpcs.liquidity;

import android.content.Context;
import android.provider.Settings;

import com.dhpcs.liquidity.models.PublicKey;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x500.X500NameBuilder;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.asn1.x509.BasicConstraints;
import org.spongycastle.asn1.x509.Extension;
import org.spongycastle.asn1.x509.Time;
import org.spongycastle.cert.CertIOException;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Locale;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

public class ClientKey {

    private static final String KEYSTORE_FILENAME = "client.keystore";
    private static final String ENTRY_ALIAS = "identity";

    private static volatile ClientKey instance;

    public static ClientKey getInstance(Context context) {
        if (instance == null) {
            synchronized (ClientKey.class) {
                if (instance == null) {
                    instance = new ClientKey(context);
                }
            }
        }
        return instance;
    }

    private static KeyStore generate(String androidId, KeyPair keyPair) {
        X500Name clientIdentity = new X500NameBuilder().addRDN(BCStyle.CN, androidId).build();

        if (keyPair == null) {
            try {
                keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new Error(e);
            }
        }

        Calendar now = Calendar.getInstance();
        Calendar expiry = Calendar.getInstance();
        expiry.setTime(now.getTime());
        expiry.add(Calendar.YEAR, 10);
        try {
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(
                            clientIdentity,
                            BigInteger.ONE,
                            new Time(now.getTime(), Locale.US),
                            new Time(expiry.getTime(), Locale.US),
                            clientIdentity,
                            keyPair.getPublic()
                    ).addExtension(
                            Extension.subjectKeyIdentifier,
                            false,
                            new JcaX509ExtensionUtils()
                                    .createSubjectKeyIdentifier(keyPair.getPublic())
                    ).addExtension(
                            Extension.authorityKeyIdentifier,
                            false,
                            new JcaX509ExtensionUtils()
                                    .createAuthorityKeyIdentifier(keyPair.getPublic())
                    ).addExtension(
                            Extension.basicConstraints,
                            false,
                            new BasicConstraints(true)
                    ).build(
                            new JcaContentSignerBuilder("SHA256withRSA").build(
                                    keyPair.getPrivate()
                            )
                    )
            );

            try {
                certificate.verify(keyPair.getPublic());
            } catch (CertificateException
                    | SignatureException
                    | NoSuchProviderException
                    | InvalidKeyException
                    | NoSuchAlgorithmException e) {
                throw new Error(e);
            }

            try {
                KeyStore result = KeyStore.getInstance("BKS");
                try {
                    result.load(null, null);
                } catch (IOException
                        | NoSuchAlgorithmException
                        | CertificateException e) {
                    throw new Error(e);
                }
                try {
                    result.setKeyEntry(
                            ENTRY_ALIAS,
                            keyPair.getPrivate(),
                            null,
                            new Certificate[]{certificate}
                    );
                } catch (KeyStoreException e) {
                    throw new Error(e);
                }
                return result;
            } catch (KeyStoreException e) {
                throw new Error(e);
            }
        } catch (CertificateException
                | OperatorCreationException
                | NoSuchAlgorithmException
                | CertIOException e) {
            throw new Error(e);
        }
    }

    private static KeyStore getOrGenerate(Context context) {
        File keyStoreFile = new File(context.getFilesDir(), KEYSTORE_FILENAME);
        if (!keyStoreFile.exists()) {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            return saveKeyStore(
                    keyStoreFile,
                    generate(androidId, null)
            );
        } else {
            KeyStore keyStore = loadKeyStore(keyStoreFile);
            try {
                X509Certificate certificate =
                        (X509Certificate) keyStore.getCertificateChain(ENTRY_ALIAS)[0];
                if (Calendar.getInstance().getTime().after(certificate.getNotAfter())) {
                    String androidId = Settings.Secure.getString(
                            context.getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    );
                    try {
                        keyStore = saveKeyStore(
                                keyStoreFile,
                                generate(
                                        androidId,
                                        new KeyPair(
                                                certificate.getPublicKey(),
                                                (PrivateKey) keyStore.getKey(ENTRY_ALIAS, null)
                                        )
                                )
                        );
                    } catch (KeyStoreException
                            | NoSuchAlgorithmException
                            | UnrecoverableKeyException
                            e) {
                        throw new Error(e);
                    }
                }
                return keyStore;
            } catch (KeyStoreException e) {
                throw new Error(e);
            }
        }
    }

    private static KeyStore loadKeyStore(File keyStoreFile) {
        try {
            KeyStore keyStore = KeyStore.getInstance("BKS");
            try {
                FileInputStream keyStoreFileInputStream = new FileInputStream(keyStoreFile);
                try {
                    keyStore.load(keyStoreFileInputStream, null);
                } catch (IOException
                        | NoSuchAlgorithmException
                        | CertificateException e) {
                    throw new Error(e);
                }
            } catch (FileNotFoundException e) {
                throw new Error(e);
            }
            return keyStore;
        } catch (KeyStoreException e) {
            throw new Error(e);
        }
    }

    private static KeyStore saveKeyStore(File keyStoreFile, KeyStore keyStore) {
        try {
            FileOutputStream keyStoreFileOutputStream = new FileOutputStream(keyStoreFile);
            try {
                keyStore.store(keyStoreFileOutputStream, null);
            } catch (KeyStoreException
                    | IOException
                    | NoSuchAlgorithmException
                    | CertificateException e) {
                throw new Error(e);
            }
        } catch (FileNotFoundException e) {
            throw new Error(e);
        }
        return keyStore;
    }

    private final KeyStore keyStore;

    private ClientKey(Context context) {
        keyStore = getOrGenerate(context);
    }

    public KeyManager[] getKeyManagers() {
        try {
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            try {
                keyManagerFactory.init(keyStore, null);
                return keyManagerFactory.getKeyManagers();
            } catch (KeyStoreException
                    | NoSuchAlgorithmException
                    | UnrecoverableKeyException e) {
                throw new Error(e);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    public PublicKey getPublicKey() {
        try {
            return new PublicKey(
                    keyStore.getCertificateChain(ENTRY_ALIAS)[0].getPublicKey().getEncoded()
            );
        } catch (KeyStoreException e) {
            throw new Error(e);
        }
    }

}
