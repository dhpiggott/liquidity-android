package com.dhpcs.liquidity

import java.io.InputStream
import java.security.cert.{CertificateException, X509Certificate}
import java.security.{KeyStore, PublicKey}
import javax.net.ssl.{TrustManager, X509TrustManager}

import okio.ByteString

object ServerTrust {

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
          s"Unknown public key: ${ByteString.of(publicKey.getEncoded: _*).base64}"
        )
      }
    }

    override def getAcceptedIssuers = Array.empty[X509Certificate]

  })

  private var trustedKeys: Set[PublicKey] = _

  def getTrustManagers(keyStoreInputStreams: Set[InputStream]) = {
    loadTrustedKeys(keyStoreInputStreams)
    TrustManagers
  }

  private def loadTrustedKeys(keyStoreInputStreams: Set[InputStream]) {
    if (trustedKeys == null) {
      trustedKeys = keyStoreInputStreams.map { keyStoreInputStream =>
        val keyStore = KeyStore.getInstance("BKS")
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
