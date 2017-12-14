package com.dhpcs.liquidity

import com.dhpcs.liquidity.proto.ws.protocol.WsProtocol
import com.google.protobuf.ByteString
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.io.IOException
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SSLException

@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class ServerConnection(filesDir: File,
                       connectivityStatePublisherProvider:
                       ConnectivityStatePublisherProvider
) : WebSocketListener() {

    companion object {

        interface ConnectivityStatePublisherProvider {

            fun provide(serverConnection: ServerConnection): ConnectivityStatePublisher

        }

        interface ConnectivityStatePublisher {

            fun isConnectionAvailable(): Boolean

            fun register()

            fun unregister()

        }

        enum class ConnectionState {
            UNAVAILABLE,
            GENERAL_FAILURE,
            TLS_ERROR,
            AVAILABLE,
            CONNECTING,
            AUTHENTICATING,
            ONLINE,
            DISCONNECTING
        }

        interface ConnectionStateListener {

            fun onConnectionStateChanged(connectionState: ConnectionState)

        }

        interface NotificationReceiptListener {

            fun onZoneNotificationReceived(notificationZoneId: String,
                                           zoneNotification: WsProtocol.ZoneNotification
            )

        }

        class ConnectionRequestToken

        private sealed class State {
            companion object {
                internal sealed class IdleState : State() {
                    companion object {
                        internal object UnavailableIdleState : IdleState()
                        internal object GeneralFailureIdleState : IdleState()
                        internal object TlsErrorIdleState : IdleState()
                        internal object AvailableIdleState : IdleState()
                    }
                }

                internal data class ActiveState(val executorService: ExecutorService) : State() {
                    var subState: SubState? = null
                }
            }
        }

        private sealed class SubState {
            companion object {
                internal data class ConnectingSubState(val webSocket: WebSocket) : SubState()
                internal sealed class ConnectedSubState : SubState() {
                    companion object {
                        internal data class AuthenticatingSubState(
                                override val webSocket: WebSocket
                        ) : ConnectedSubState()

                        internal data class OnlineSubState(
                                override val webSocket: WebSocket
                        ) : ConnectedSubState()
                    }

                    abstract val webSocket: WebSocket
                }

                internal object DisconnectingSubState : SubState()
            }
        }

        enum class CloseCause {
            GENERAL_FAILURE,
            TLS_ERROR,
            SERVER_DISCONNECT,
            CLIENT_DISCONNECT
        }

    }

    private val clientKeyStore by lazy { ClientKeyStore(filesDir) }

    private val okHttpClient = OkHttpClient()

    private val connectivityStatePublisher = connectivityStatePublisherProvider.provide(
            this
    )

    private var pendingRequests = emptyMap<Long, SingleEmitter<in WsProtocol.ZoneResponse>>()
    private var nextCorrelationId = 0L
    private var state: State = State.Companion.IdleState.Companion.UnavailableIdleState
    private var hasFailed: Boolean = false

    private var _connectionState: ConnectionState = ConnectionState.UNAVAILABLE
    private var connectionStateListeners = emptySet<ConnectionStateListener>()
    private var connectRequestTokens = emptySet<ConnectionRequestToken>()

    private var notificationReceiptListeners = emptySet<NotificationReceiptListener>()

    init {
        handleConnectivityStateChange()
    }

    val clientKey: com.google.protobuf.ByteString by lazy {
        ByteString.copyFrom(clientKeyStore.publicKey.encoded)
    }

    val connectionState get() = _connectionState

    fun registerListener(listener: ConnectionStateListener) {
        if (!connectionStateListeners.contains(listener)) {
            if (connectionStateListeners.isEmpty()) {
                connectivityStatePublisher.register()
                handleConnectivityStateChange()
            }
            connectionStateListeners += listener
            listener.onConnectionStateChanged(_connectionState)
        }
    }

    fun registerListener(listener: NotificationReceiptListener) {
        if (!notificationReceiptListeners.contains(listener)) {
            notificationReceiptListeners += listener
        }
    }

    fun requestConnection(token: ConnectionRequestToken, retry: Boolean) {
        if (!connectRequestTokens.contains(token)) connectRequestTokens += token
        if ((_connectionState == ConnectionState.AVAILABLE
                || _connectionState == ConnectionState.GENERAL_FAILURE
                || _connectionState == ConnectionState.TLS_ERROR)
                && (!hasFailed || retry)) {
            connect()
        }
    }

    fun sendCreateZoneCommand(createZoneCommand: WsProtocol.ZoneCommand.CreateZoneCommand
    ): Single<WsProtocol.ZoneResponse> {
        return sendCommand { correlationId ->
            WsProtocol.ServerMessage.Command.newBuilder()
                    .setCorrelationId(correlationId)
                    .setCreateZoneCommand(createZoneCommand)
                    .build()
        }
    }

    fun sendZoneCommand(zoneId: String, zoneCommand: WsProtocol.ZoneCommand
    ): Single<WsProtocol.ZoneResponse> {
        return sendCommand { correlationId ->
            WsProtocol.ServerMessage.Command.newBuilder()
                    .setCorrelationId(correlationId)
                    .setZoneCommandEnvelope(
                            WsProtocol.ServerMessage.Command.ZoneCommandEnvelope.newBuilder()
                                    .setZoneId(zoneId)
                                    .setZoneCommand(zoneCommand)
                    )
                    .build()
        }
    }

    fun unrequestConnection(token: ConnectionRequestToken) {
        if (connectRequestTokens.contains(token)) {
            connectRequestTokens -= token
            if (connectRequestTokens.isEmpty()) {
                if (_connectionState == ConnectionState.CONNECTING
                        || _connectionState == ConnectionState.ONLINE) {
                    disconnect(1001)
                }
            }
        }
    }

    fun unregisterListener(listener: NotificationReceiptListener) {
        if (notificationReceiptListeners.contains(listener)) {
            notificationReceiptListeners -= listener
        }
    }

    fun unregisterListener(listener: ConnectionStateListener) {
        if (connectionStateListeners.contains(listener)) {
            connectionStateListeners -= listener
            if (connectionStateListeners.isEmpty()) connectivityStatePublisher.unregister()
        }
    }

    fun handleConnectivityStateChange() {
        if (!connectivityStatePublisher.isConnectionAvailable()) {
            when (state) {
                State.Companion.IdleState.Companion.AvailableIdleState,
                State.Companion.IdleState.Companion.GeneralFailureIdleState,
                State.Companion.IdleState.Companion.TlsErrorIdleState -> {
                    state = State.Companion.IdleState.Companion.UnavailableIdleState
                    _connectionState = ConnectionState.UNAVAILABLE
                    connectionStateListeners.forEach {
                        it.onConnectionStateChanged(_connectionState)
                    }
                }
                else -> {
                }
            }
        } else {
            when (state) {
                State.Companion.IdleState.Companion.UnavailableIdleState -> {
                    state = State.Companion.IdleState.Companion.AvailableIdleState
                    _connectionState = ConnectionState.AVAILABLE
                    connectionStateListeners.forEach {
                        it.onConnectionStateChanged(_connectionState)
                    }
                }
                else -> {
                }
            }
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        AndroidSchedulers.mainThread().scheduleDirect {
            val state = state
            when (state) {
                is State.Companion.IdleState ->
                    throw IllegalStateException("Already disconnected")
                is State.Companion.ActiveState ->
                    state.executorService.execute {
                        when (state.subState) {
                            is SubState.Companion.ConnectingSubState ->
                                throw IllegalStateException("Not connected or disconnecting")
                            is SubState.Companion.ConnectedSubState.Companion
                            .AuthenticatingSubState,
                            is SubState.Companion.ConnectedSubState.Companion
                            .OnlineSubState ->
                                doClose(state.executorService, CloseCause.SERVER_DISCONNECT)
                            is SubState.Companion.DisconnectingSubState ->
                                doClose(state.executorService, CloseCause.CLIENT_DISCONNECT)
                        }
                    }
            }
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        AndroidSchedulers.mainThread().scheduleDirect {
            val state = state
            when (state) {
                is State.Companion.IdleState ->
                    throw IllegalStateException("Already disconnected")
                is State.Companion.ActiveState ->
                    state.executorService.execute {
                        when (state.subState) {
                            is SubState.Companion.DisconnectingSubState ->
                                doClose(state.executorService, CloseCause.CLIENT_DISCONNECT)
                            else -> {
                                if (response == null) {
                                    when (t) {
                                        is SSLException ->
                                            // Client rejected server certificate.
                                            doClose(state.executorService, CloseCause.TLS_ERROR)
                                        else ->
                                            doClose(
                                                    state.executorService,
                                                    CloseCause.GENERAL_FAILURE
                                            )
                                    }
                                } else {
                                    doClose(state.executorService, CloseCause.GENERAL_FAILURE)
                                }
                            }
                        }
                    }
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
        val clientMessage = WsProtocol.ClientMessage.parseFrom(bytes.toByteArray())
        AndroidSchedulers.mainThread().scheduleDirect {
            val state = state
            when (state) {
                is State.Companion.IdleState ->
                    throw IllegalStateException("Not connected")
                is State.Companion.ActiveState -> {
                    when (clientMessage.messageCase) {
                        WsProtocol.ClientMessage.MessageCase.MESSAGE_NOT_SET -> {
                        }
                        WsProtocol.ClientMessage.MessageCase.KEY_OWNERSHIP_CHALLENGE -> {
                            val keyOwnershipProof = createKeyOwnershipProof(
                                    clientKeyStore.publicKey,
                                    clientKeyStore.privateKey,
                                    clientMessage.keyOwnershipChallenge
                            )
                            sendServerMessage(
                                    webSocket,
                                    WsProtocol.ServerMessage.newBuilder()
                                            .setKeyOwnershipProof(keyOwnershipProof)
                                            .build()
                            )
                            state.executorService.execute {
                                state.subState = SubState.Companion.ConnectedSubState.Companion
                                        .OnlineSubState(webSocket)
                                AndroidSchedulers.mainThread().scheduleDirect {
                                    _connectionState = ConnectionState.ONLINE
                                    connectionStateListeners.forEach {
                                        it.onConnectionStateChanged(_connectionState)
                                    }
                                }
                            }
                        }
                        WsProtocol.ClientMessage.MessageCase.COMMAND -> {
                            state.executorService.execute {
                                when (state.subState) {
                                    is SubState.Companion.ConnectingSubState ->
                                        throw IllegalStateException("Not connected")
                                    is SubState.Companion.ConnectedSubState.Companion
                                    .AuthenticatingSubState,
                                    is SubState.Companion.ConnectedSubState.Companion
                                    .OnlineSubState -> {
                                        when (clientMessage.command.commandCase) {
                                            WsProtocol.ClientMessage.Command.CommandCase
                                                    .COMMAND_NOT_SET -> {
                                            }
                                            WsProtocol.ClientMessage.Command.CommandCase
                                                    .PING_COMMAND -> {
                                                val response = WsProtocol.ServerMessage.Response
                                                        .newBuilder()
                                                        .setCorrelationId(
                                                                clientMessage.command.correlationId
                                                        )
                                                        .setPingResponse(
                                                                com.google.protobuf.ByteString.EMPTY
                                                        )
                                                sendServerMessage(
                                                        webSocket,
                                                        WsProtocol.ServerMessage.newBuilder()
                                                                .setResponse(response)
                                                                .build()
                                                )
                                            }
                                        }
                                    }
                                    SubState.Companion.DisconnectingSubState -> {
                                    }
                                }
                            }
                        }
                        WsProtocol.ClientMessage.MessageCase.RESPONSE -> {
                            state.executorService.execute {
                                when (state.subState) {
                                    is SubState.Companion.ConnectingSubState ->
                                        throw IllegalStateException("Not connected")
                                    is SubState.Companion.ConnectedSubState.Companion
                                    .AuthenticatingSubState ->
                                        throw IllegalStateException("Authenticating")
                                    is SubState.Companion.ConnectedSubState.Companion
                                    .OnlineSubState -> {
                                        val correlationId = clientMessage.response.correlationId
                                        val singleObserver = pendingRequests[correlationId]
                                        singleObserver?.let {
                                            pendingRequests -= correlationId
                                            when (clientMessage.response.responseCase) {
                                                WsProtocol.ClientMessage.Response.ResponseCase
                                                        .RESPONSE_NOT_SET ->
                                                    throw IllegalStateException(
                                                            "Empty or unsupported response"
                                                    )
                                                WsProtocol.ClientMessage.Response.ResponseCase
                                                        .ZONE_RESPONSE -> {
                                                    singleObserver.onSuccess(
                                                            clientMessage.response.zoneResponse
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is SubState.Companion.DisconnectingSubState -> {
                                    }
                                }
                            }
                        }
                        WsProtocol.ClientMessage.MessageCase.NOTIFICATION -> {
                            state.executorService.execute {
                                when (clientMessage.notification.notificationCase) {
                                    WsProtocol.ClientMessage.Notification.NotificationCase
                                            .NOTIFICATION_NOT_SET -> {
                                    }
                                    WsProtocol.ClientMessage.Notification.NotificationCase
                                            .ZONE_NOTIFICATION_ENVELOPE -> {
                                        val zoneId = clientMessage.notification
                                                .zoneNotificationEnvelope.zoneId
                                        val zoneNotification = clientMessage.notification
                                                .zoneNotificationEnvelope.zoneNotification
                                        when (state.subState) {
                                            is SubState.Companion.ConnectingSubState ->
                                                throw IllegalStateException("Not connected")
                                            is SubState.Companion.ConnectedSubState.Companion
                                            .AuthenticatingSubState ->
                                                throw IllegalStateException("Authenticating")
                                            is SubState.Companion.ConnectedSubState.Companion
                                            .OnlineSubState ->
                                                AndroidSchedulers.mainThread().scheduleDirect {
                                                    notificationReceiptListeners.forEach {
                                                        it.onZoneNotificationReceived(
                                                                zoneId,
                                                                zoneNotification
                                                        )
                                                    }
                                                }
                                            is SubState.Companion.DisconnectingSubState -> {
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        AndroidSchedulers.mainThread().scheduleDirect {
            val state = state
            when (state) {
                is State.Companion.IdleState ->
                    throw IllegalStateException("Not connecting")
                is State.Companion.ActiveState ->
                    state.executorService.execute {
                        when (state.subState) {
                            is SubState.Companion.ConnectedSubState,
                            SubState.Companion.DisconnectingSubState ->
                                throw IllegalStateException("Not connecting")
                            is SubState.Companion.ConnectingSubState -> {
                                state.subState = SubState.Companion.ConnectedSubState.Companion
                                        .AuthenticatingSubState(webSocket)
                                AndroidSchedulers.mainThread().scheduleDirect {
                                    _connectionState = ConnectionState.AUTHENTICATING
                                    connectionStateListeners.forEach {
                                        it.onConnectionStateChanged(_connectionState)
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun connect() = when (state) {
        is State.Companion.ActiveState ->
            throw IllegalStateException("Already connecting/connected/disconnecting")
        is State.Companion.IdleState.Companion.UnavailableIdleState ->
            throw IllegalStateException("Connection unavailable")
        is State.Companion.IdleState.Companion.AvailableIdleState,
        State.Companion.IdleState.Companion.GeneralFailureIdleState,
        State.Companion.IdleState.Companion.TlsErrorIdleState ->
            doOpen()
    }

    private fun disconnect(code: Int) {
        val state = state
        when (state) {
            is State.Companion.IdleState ->
                throw IllegalStateException("Already disconnected")
            is State.Companion.ActiveState ->
                state.executorService.execute {
                    val subState = state.subState
                    when (subState) {
                        is SubState.Companion.DisconnectingSubState ->
                            throw IllegalStateException("Already disconnecting")
                        is SubState.Companion.ConnectingSubState -> {
                            state.subState = SubState.Companion.DisconnectingSubState
                            AndroidSchedulers.mainThread().scheduleDirect {
                                _connectionState = ConnectionState.DISCONNECTING
                                connectionStateListeners.forEach {
                                    it.onConnectionStateChanged(_connectionState)
                                }
                            }
                            subState.webSocket.cancel()
                        }
                        is SubState.Companion.ConnectedSubState.Companion
                        .AuthenticatingSubState -> {
                            try {
                                subState.webSocket.close(code, null)
                            } catch (_: IOException) {
                            }
                        }
                        is SubState.Companion.ConnectedSubState.Companion.OnlineSubState -> {
                            state.subState = SubState.Companion.DisconnectingSubState
                            AndroidSchedulers.mainThread().scheduleDirect {
                                _connectionState = ConnectionState.DISCONNECTING
                                connectionStateListeners.forEach {
                                    it.onConnectionStateChanged(_connectionState)
                                }
                            }
                            try {
                                subState.webSocket.close(code, null)
                            } catch (_: IOException) {
                            }
                        }
                        else -> {
                        }
                    }
                }
        }
    }

    private fun doOpen() {
        val activeState = State.Companion.ActiveState(Executors.newSingleThreadExecutor())
        state = activeState
        activeState.executorService.submit {
            val webSocket = okHttpClient.newWebSocket(
                    okhttp3.Request.Builder().url("https://api.liquidityapp.com/ws").build(),
                    this
            )
            activeState.subState = SubState.Companion.ConnectingSubState(webSocket)
        }
        _connectionState = ConnectionState.CONNECTING
        connectionStateListeners.forEach {
            it.onConnectionStateChanged(_connectionState)
        }
    }

    private fun doClose(executorService: ExecutorService, closeCause: CloseCause) {
        executorService.shutdown()
        nextCorrelationId = 0
        pendingRequests = emptyMap()
        AndroidSchedulers.mainThread().scheduleDirect {
            when (closeCause) {
                CloseCause.GENERAL_FAILURE -> {
                    hasFailed = true
                    state = State.Companion.IdleState.Companion.GeneralFailureIdleState
                    _connectionState = ConnectionState.GENERAL_FAILURE
                    connectionStateListeners.forEach {
                        it.onConnectionStateChanged(_connectionState)
                    }
                }
                CloseCause.TLS_ERROR -> {
                    hasFailed = true
                    state = State.Companion.IdleState.Companion.TlsErrorIdleState
                    _connectionState = ConnectionState.TLS_ERROR
                    connectionStateListeners.forEach {
                        it.onConnectionStateChanged(_connectionState)
                    }
                }
                CloseCause.SERVER_DISCONNECT -> {
                    hasFailed = true
                    state = State.Companion.IdleState.Companion.AvailableIdleState
                    _connectionState = ConnectionState.AVAILABLE
                    connectionStateListeners.forEach {
                        it.onConnectionStateChanged(_connectionState)
                    }
                }
                CloseCause.CLIENT_DISCONNECT -> {
                    hasFailed = false
                    if (connectRequestTokens.isNotEmpty())
                        doOpen()
                    else {
                        state = State.Companion.IdleState.Companion.AvailableIdleState
                        _connectionState = ConnectionState.AVAILABLE
                        connectionStateListeners.forEach {
                            it.onConnectionStateChanged(_connectionState)
                        }
                    }
                }
            }
        }
    }

    private fun createKeyOwnershipProof(publicKey: RSAPublicKey,
                                        privateKey: RSAPrivateKey,
                                        keyOwnershipChallenge:
                                        WsProtocol.ClientMessage.KeyOwnershipChallenge
    ): WsProtocol.ServerMessage.KeyOwnershipProof {
        fun signMessage(privateKey: RSAPrivateKey, message: ByteArray): ByteArray {
            val s = Signature.getInstance("SHA256withRSA")
            s.initSign(privateKey)
            s.update(message)
            return s.sign()
        }

        val nonce = keyOwnershipChallenge.nonce.toByteArray()
        return WsProtocol.ServerMessage.KeyOwnershipProof.newBuilder()
                .setPublicKey(com.google.protobuf.ByteString.copyFrom(publicKey.encoded))
                .setSignature(
                        com.google.protobuf.ByteString.copyFrom(signMessage(privateKey, nonce))
                )
                .build()
    }

    private fun sendCommand(command: (Long) -> WsProtocol.ServerMessage.Command
    ): Single<WsProtocol.ZoneResponse> {
        val state = state
        return when (state) {
            is State.Companion.IdleState ->
                Single.error(IllegalStateException("Not connected"))
            is State.Companion.ActiveState -> {
                Single.create<WsProtocol.ZoneResponse> { emitter ->
                    state.executorService.execute {
                        val subState = state.subState
                        when (subState) {
                            is SubState.Companion.ConnectingSubState,
                            SubState.Companion.DisconnectingSubState ->
                                emitter.onError(IllegalStateException("Not connected"))
                            is SubState.Companion.ConnectedSubState
                            .Companion.AuthenticatingSubState ->
                                emitter.onError(IllegalStateException("Authenticating"))
                            is SubState.Companion.ConnectedSubState.Companion.OnlineSubState -> {
                                val correlationId = nextCorrelationId
                                nextCorrelationId += 1
                                pendingRequests += Pair(correlationId, emitter)
                                sendServerMessage(
                                        subState.webSocket,
                                        WsProtocol.ServerMessage.newBuilder()
                                                .setCommand(command(correlationId))
                                                .build()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendServerMessage(webSocket: WebSocket, message: WsProtocol.ServerMessage) {
        webSocket.send(okio.ByteString.of(*message.toByteArray()))
    }
}
