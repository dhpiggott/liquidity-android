package com.dhpcs.liquidity

import com.dhpcs.liquidity.proto.ws.protocol.WsProtocol
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import okhttp3.*
import okio.ByteString
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

class ServerConnection(filesDir: File) {

    companion object {

        private val okHttpClient = OkHttpClient.Builder()
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
                .build()

    }

    private val clientKeyStore by lazy { ClientKeyStore(filesDir) }

    val clientKey: com.google.protobuf.ByteString by lazy {
        com.google.protobuf.ByteString.copyFrom(clientKeyStore.publicKey.encoded)
    }

    fun createZone(
            createZoneCommand: WsProtocol.ZoneCommand.CreateZoneCommand
    ): Single<WsProtocol.ZoneResponse> {
        return execZoneCommand("", createZoneCommand.toByteArray())
    }

    fun zoneNotifications(zoneId: String): Observable<WsProtocol.ZoneNotification> {
        return Observable.create<WsProtocol.ZoneNotification>
        {
            val call = okHttpClient.newCall(
                    Request.Builder()
                            .url("https://api.liquidityapp.com/zone/$zoneId")
                            .header("Authorization", "Bearer ${selfSignedJwt()}")
                            .header("Accept", "application/x-protobuf; delimited=true")
                            .get()
                            .build()
            )
            it.setDisposable(Disposables.fromRunnable { call.cancel() })
            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    if (!it.isDisposed) it.tryOnError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val source = response.body()!!.source()
                    if (response.code() != 200) {
                        if (!it.isDisposed) {
                            it.tryOnError(IOException(
                                    "response.code() != 200 (response.code() = ${response.code()})"
                            ))
                        }
                    } else {
                        fun exhausted(): Boolean {
                            return try {
                                source.exhausted()
                            } catch (_: IOException) {
                                true
                            }
                        }
                        while (!exhausted()) {
                            it.onNext(WsProtocol.ZoneNotification.parseDelimitedFrom(
                                    source.inputStream()
                            ))
                        }
                        it.onComplete()
                    }
                    source.close()
                }

            })
        }.observeOn(AndroidSchedulers.mainThread())
    }

    fun execZoneCommand(zoneId: String, zoneCommand: WsProtocol.ZoneCommand
    ): Single<WsProtocol.ZoneResponse> {
        return execZoneCommand(
                "/${URLEncoder.encode(zoneId, StandardCharsets.UTF_8.name())}",
                zoneCommand.toByteArray()
        )
    }

    private fun execZoneCommand(
            zoneSubPath: String,
            entity: ByteArray
    ): Single<WsProtocol.ZoneResponse> {
        return Single.create<WsProtocol.ZoneResponse>
        {
            val call = okHttpClient.newCall(
                    Request.Builder()
                            .url("https://api.liquidityapp.com/zone$zoneSubPath")
                            .header("Authorization", "Bearer ${selfSignedJwt()}")
                            .header("Accept", "application/x-protobuf")
                            .put(RequestBody.create(
                                    MediaType.parse("application/x-protobuf"),
                                    entity
                            ))
                            .build()
            )
            it.setDisposable(Disposables.fromRunnable { call.cancel() })
            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    if (!it.isDisposed) it.tryOnError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code() != 200) {
                        if (!it.isDisposed) {
                            it.tryOnError(IOException(
                                    "response.code() != 200 (response.code() = ${response.code()})"
                            ))
                        }
                        response.body()!!.source().close()
                    } else {
                        it.onSuccess(WsProtocol.ZoneResponse.parseFrom(
                                response.body()!!.bytes()
                        ))
                    }
                }

            })
        }.observeOn(AndroidSchedulers.mainThread())
    }

    private fun selfSignedJwt(): String {
        val now = Date()
        val jwt = SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                JWTClaimsSet.Builder()
                        .subject(ByteString.of(*clientKeyStore.publicKey.encoded).base64())
                        .issueTime(Date(now.time))
                        .notBeforeTime(Date(now.time - TimeUnit.MINUTES.toMillis(1)))
                        .expirationTime(Date(now.time + TimeUnit.MINUTES.toMillis(1)))
                        .build()
        )
        jwt.sign(RSASSASigner(clientKeyStore.privateKey))
        return jwt.serialize()
    }

}
