package com.dhpcs.liquidity

import com.dhpcs.liquidity.proto.grpc.protocol.GrpcProtocol
import com.dhpcs.liquidity.proto.grpc.protocol.LiquidityServiceGrpc
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.grpc.CallCredentials
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import java.io.File
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ServerConnection(filesDir: File) {

    private val clientKeyStore by lazy { ClientKeyStore(filesDir) }

    private val callCredentials = object : CallCredentials() {

        override fun applyRequestMetadata(
                requestInfo: RequestInfo?,
                appExecutor: Executor?,
                applier: MetadataApplier?) {
            val metadata = Metadata()
            val now = Date()
            val jwt = SignedJWT(
                    JWSHeader.Builder(JWSAlgorithm.RS256)
                            .build(),
                    JWTClaimsSet.Builder()
                            .subject(
                                    com.nimbusds.jose.util.Base64.encode(
                                            clientKeyStore.publicKey.encoded
                                    ).toString()
                            )
                            .issueTime(
                                    Date(now.time)
                            )
                            .notBeforeTime(
                                    Date(now.time - TimeUnit.MINUTES.toMillis(1))
                            )
                            .expirationTime(
                                    Date(now.time + TimeUnit.MINUTES.toMillis(1))
                            )
                            .build()
            )
            jwt.sign(RSASSASigner(clientKeyStore.privateKey))
            metadata.put(
                    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "bearer ${jwt.serialize()}"
            )
            applier!!.apply(metadata)
        }

        override fun thisUsesUnstableApi() {}

    }

    private val channel = ManagedChannelBuilder.forAddress(
            "api.liquidityapp.com",
            443
    ).build()

    val clientKey: com.google.protobuf.ByteString by lazy {
        com.google.protobuf.ByteString.copyFrom(clientKeyStore.publicKey.encoded)
    }

    val commandStub: LiquidityServiceGrpc.LiquidityServiceFutureStub = LiquidityServiceGrpc
            .newFutureStub(channel)
            .withCallCredentials(callCredentials)

    fun zoneNotifications(zoneId: String): Observable<GrpcProtocol.ZoneNotification> {
        return Observable.create<GrpcProtocol.ZoneNotification> {
            LiquidityServiceGrpc.newStub(channel)
                    .withCallCredentials(callCredentials)
                    .zoneNotifications(
                            GrpcProtocol.ZoneSubscription.newBuilder()
                                    .setZoneId(zoneId)
                                    .build(),
                            object : ClientResponseObserver<
                                    GrpcProtocol.ZoneSubscription,
                                    GrpcProtocol.ZoneNotification
                                    > {

                                override fun beforeStart(
                                        requestStream:
                                        ClientCallStreamObserver<GrpcProtocol.ZoneSubscription>
                                ) {
                                    it.setDisposable(Disposables.fromAction {
                                        requestStream.cancel(null, null)
                                    })
                                    if (it.isDisposed) {
                                        requestStream.cancel(null, null)
                                    }
                                }

                                override fun onError(t: Throwable?) {
                                    it.tryOnError(t!!)
                                }

                                override fun onNext(value: GrpcProtocol.ZoneNotification?) {
                                    it.onNext(value!!)
                                }

                                override fun onCompleted() {
                                    it.onComplete()
                                }

                            }
                    )
        }.observeOn(AndroidSchedulers.mainThread())
    }

}
