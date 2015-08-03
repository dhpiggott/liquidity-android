package com.dhpcs.liquidity

import java.io.{File, FileInputStream, FileOutputStream}
import java.math.BigInteger
import java.security.cert.Certificate
import java.security.{KeyPairGenerator, KeyStore}
import java.util.{Calendar, Locale}
import javax.net.ssl.{KeyManager, KeyManagerFactory}

import android.content.Context
import android.provider.Settings
import com.dhpcs.liquidity.models.PublicKey
import org.spongycastle.asn1.x500.X500NameBuilder
import org.spongycastle.asn1.x500.style.BCStyle
import org.spongycastle.asn1.x509.{BasicConstraints, Extension, Time}
import org.spongycastle.asn1.{ASN1GeneralizedTime, ASN1UTCTime}
import org.spongycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509ExtensionUtils, JcaX509v3CertificateBuilder}
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder

object ClientKey {

  private val KeystoreFilename = "client.keystore"
  private val EntryAlias = "identity"

  private var keyStore: KeyStore = _
  private var publicKey: PublicKey = _
  private var keyManagers: Array[KeyManager] = _

  private def generateCertKeyPair(context: Context) = {
    val androidId = Settings.Secure.getString(
      context.getContentResolver,
      Settings.Secure.ANDROID_ID
    )
    val clientIdentity = new X500NameBuilder().addRDN(BCStyle.CN, androidId).build
    val keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair
    val certificate = new JcaX509CertificateConverter().getCertificate(
      new JcaX509v3CertificateBuilder(
        clientIdentity,
        BigInteger.ONE,
        new Time(new ASN1UTCTime(Calendar.getInstance.getTime, Locale.US)),
        new Time(new ASN1GeneralizedTime("99991231235959Z")),
        clientIdentity,
        keyPair.getPublic
      ).addExtension(
          Extension.subjectKeyIdentifier,
          false,
          new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.getPublic)
        ).addExtension(
          Extension.authorityKeyIdentifier,
          false,
          new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(keyPair.getPublic)
        ).addExtension(
          Extension.basicConstraints,
          false,
          new BasicConstraints(true)
        ).build(
          new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate)
        )
    )
    (certificate, keyPair.getPrivate)
  }

  def getKeyManagers(context: Context) = {
    if (keyManagers == null) {
      val keyStore = getOrLoadOrCreateKeyStore(context)
      val keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm
      )
      keyManagerFactory.init(keyStore, null)
      keyManagers = keyManagerFactory.getKeyManagers
    }
    keyManagers
  }

  private def getOrLoadOrCreateKeyStore(context: Context) = {
    if (keyStore == null) {
      keyStore = KeyStore.getInstance("BKS")
      val keyStoreFile = new File(context.getFilesDir, KeystoreFilename)
      if (!keyStoreFile.exists) {
        val (certificate, privateKey) = generateCertKeyPair(context)
        keyStore.load(null, null)
        keyStore.setKeyEntry(
          EntryAlias,
          privateKey,
          null,
          Array[Certificate](certificate)
        )
        keyStore.store(new FileOutputStream(keyStoreFile), null)
      } else {
        keyStore.load(new FileInputStream(keyStoreFile), null)
      }
    }
    keyStore
  }

  def getPublicKey(context: Context) = {
    if (publicKey == null) {
      val keyStore = getOrLoadOrCreateKeyStore(context)
      publicKey = PublicKey(
        keyStore.getCertificateChain(ClientKey.EntryAlias)(0).getPublicKey.getEncoded
      )
    }
    publicKey
  }

}