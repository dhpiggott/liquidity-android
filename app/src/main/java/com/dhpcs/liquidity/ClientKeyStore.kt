package com.dhpcs.liquidity

import org.spongycastle.openssl.PEMKeyPair
import org.spongycastle.openssl.PEMParser
import org.spongycastle.openssl.jcajce.JcaPEMKeyConverter
import org.spongycastle.openssl.jcajce.JcaPEMWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.FileWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class ClientKeyStore(filesDir: File) {

    companion object {

        private const val LEGACY_BKS_KEYSTORE_FILENAME = "client.keystore"
        private const val ENTRY_ALIAS = "identity"
        private const val KEY_SIZE = 2048
        private const val PRIVATE_KEY_FILENAME = "id_rsa"

        private fun readKey(privateKeyFile: File): KeyPair? {
            return if (!privateKeyFile.exists()) {
                null
            } else {
                PEMParser(FileReader(privateKeyFile)).use {
                    JcaPEMKeyConverter().getKeyPair(it.readObject() as PEMKeyPair)
                }
            }
        }

        private fun loadFromLegacyBksKeyStore(keyStoreFile: File): KeyPair? {
            return if (!keyStoreFile.exists()) {
                null
            } else {
                val keyStore = KeyStore.getInstance("BKS")
                FileInputStream(keyStoreFile).use {
                    keyStore.load(it, charArrayOf())
                }
                val publicKey = keyStore.getCertificate(ENTRY_ALIAS).publicKey
                val privateKey = keyStore.getKey(ENTRY_ALIAS, charArrayOf()) as PrivateKey
                KeyPair(publicKey, privateKey)
            }
        }

        private fun generateKey(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(KEY_SIZE)
            return keyPairGenerator.generateKeyPair()
        }

        private fun writeKey(privateKeyFile: File, keyPair: KeyPair) {
            JcaPEMWriter(FileWriter(privateKeyFile)).use {
                it.writeObject(keyPair.private)
            }
        }

        private fun deleteLegacyBksKeyStoreIfExists(keyStoreFile: File) {
            if (keyStoreFile.exists()) keyStoreFile.delete()
        }

    }

    val publicKey: RSAPublicKey
    val privateKey: RSAPrivateKey

    init {
        val keyStoreFile = File(filesDir, LEGACY_BKS_KEYSTORE_FILENAME)
        val privateKeyFile = File(filesDir, PRIVATE_KEY_FILENAME)
        val keyPair = readKey(privateKeyFile) ?: loadFromLegacyBksKeyStore(keyStoreFile)
        ?: generateKey()
        if (!privateKeyFile.exists()) writeKey(privateKeyFile, keyPair)
        deleteLegacyBksKeyStoreIfExists(keyStoreFile)
        publicKey = keyPair.public as RSAPublicKey
        privateKey = keyPair.private as RSAPrivateKey
    }

}