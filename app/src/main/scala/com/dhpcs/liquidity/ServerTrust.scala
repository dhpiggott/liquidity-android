package com.dhpcs.liquidity

import java.security.cert.{CertificateException, X509Certificate}
import java.security.{KeyStore, PublicKey}
import javax.net.ssl.{TrustManager, X509TrustManager}

import android.content.Context
import android.util.Base64

object ServerTrust {

  private val TrustStoreResources = Set(R.raw.liquidity_dhpcs_com)
  private val EntryAlias = "identity"

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
      if (!trustedKeys.contains(publicKey)) {
        throw new CertificateException(
          "Unknown public key: " + Base64.encodeToString(publicKey.getEncoded, Base64.DEFAULT)
        )
      }
    }

    override def getAcceptedIssuers = Array.empty[X509Certificate]

  })

  private var trustedKeys: Set[PublicKey] = _

  def getTrustManagers(context: Context) = {
    loadTrustedKeys(context)
    TrustManagers
  }

  private def loadTrustedKeys(context: Context) {
    if (trustedKeys == null) {
      trustedKeys = TrustStoreResources.map { trustStoreResource =>
        val keyStore = KeyStore.getInstance("BKS")
        val keyStoreInputStream = context.getResources.openRawResource(trustStoreResource)
        try {
          keyStore.load(keyStoreInputStream, Array.emptyCharArray)
        } finally {
          keyStoreInputStream.close()
        }
        keyStore.getCertificate(EntryAlias).getPublicKey
      }
    }
  }

}
