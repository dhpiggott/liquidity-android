package com.dhpcs.liquidity

import java.io.InputStreamReader
import java.security.PublicKey
import java.security.cert.{CertificateException, X509Certificate}
import javax.net.ssl.{HostnameVerifier, SSLPeerUnverifiedException, SSLSession, TrustManager, X509TrustManager}

import android.content.Context
import android.util.Base64
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo
import org.spongycastle.openssl.PEMParser
import org.spongycastle.openssl.jcajce.JcaPEMKeyConverter

object ServerTrust {

  private val HostnameVerifier = new HostnameVerifier() {

    override def verify(hostname: String, session: SSLSession) =
      trustedKeys.get(hostname).fold(false)(expectedPublicKey =>
        try {
          val publicKey = session.getPeerCertificates()(0).getPublicKey
          publicKey == expectedPublicKey
        } catch {
          case e: SSLPeerUnverifiedException => false
        }
      )

  }

  private val TrustManagers = Array[TrustManager](new X509TrustManager() {

    @throws(classOf[CertificateException])
    override def checkClientTrusted(chain: Array[X509Certificate], authType: String) =
      checkTrusted(chain)

    @throws(classOf[CertificateException])
    override def checkServerTrusted(chain: Array[X509Certificate], authType: String) =
      checkTrusted(chain)

    @throws(classOf[CertificateException])
    private def checkTrusted(chain: Array[X509Certificate]) {
      val publicKey = chain(0).getPublicKey
      if (!trustedKeys.values.exists(_ == publicKey)) {
        throw new CertificateException(
          "Unknown public key: " + Base64.encodeToString(publicKey.getEncoded, Base64.DEFAULT)
        )
      }
    }

    override def getAcceptedIssuers = Array.empty[X509Certificate]

  })

  private val TrustedKeyResources = Map(
    "liquidity.dhpcs.com" -> R.raw.liquidity_dhpcs_com
  )

  private var trustedKeys: Map[String, PublicKey] = _

  def getHostnameVerifier(context: Context) = {
    loadTrustedKeys(context)
    HostnameVerifier
  }

  def getTrustManagers(context: Context) = {
    loadTrustedKeys(context)
    TrustManagers
  }

  private def loadTrustedKeys(context: Context) {
    if (trustedKeys == null) {
      trustedKeys = TrustedKeyResources.map { case (hostname, resourceId) =>
        val pemParser = new PEMParser(
          new InputStreamReader(context.getResources.openRawResource(resourceId))
        )
        try {
          val publicKey = new JcaPEMKeyConverter().getPublicKey(
            pemParser.readObject.asInstanceOf[SubjectPublicKeyInfo]
          )
          hostname -> publicKey
        } finally {
          pemParser.close()
        }
      }
    }
  }

}
