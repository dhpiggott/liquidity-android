package com.dhpcs.liquidity;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.SparseArray;

import com.dhpcs.jsonrpc.JsonRpcMessage;
import com.dhpcs.jsonrpc.JsonRpcMessage$;
import com.dhpcs.jsonrpc.JsonRpcNotificationMessage;
import com.dhpcs.jsonrpc.JsonRpcRequestMessage;
import com.dhpcs.jsonrpc.JsonRpcResponseError;
import com.dhpcs.jsonrpc.JsonRpcResponseMessage;
import com.dhpcs.liquidity.models.Command;
import com.dhpcs.liquidity.models.Command$;
import com.dhpcs.liquidity.models.ErrorResponse;
import com.dhpcs.liquidity.models.Notification;
import com.dhpcs.liquidity.models.Notification$;
import com.dhpcs.liquidity.models.Response;
import com.dhpcs.liquidity.models.Response$;
import com.dhpcs.liquidity.models.ResultResponse;
import com.fasterxml.jackson.core.JsonParseException;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okio.Buffer;
import okio.BufferedSource;
import play.api.libs.json.JsResult;
import play.api.libs.json.Json;
import scala.Int;
import scala.Option;
import scala.util.Right;

public class ServerConnection implements WebSocketListener {

    public enum ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    public interface ConnectionStateListener {

        void onStateChanged(ConnectionState connectionState);

    }

    public interface NotificationListener {

        void onNotificationReceived(Notification notification);

    }

    public static abstract class ResponseCallback {

        void onErrorReceived(ErrorResponse errorResponse) {
            throw new RuntimeException(errorResponse.toString());
        }

        abstract void onResultReceived(ResultResponse resultResponse);

        // TODO: Timeouts?

    }

    private static final String SERVER_ENDPOINT = "https://liquidity.dhpcs.com/ws";
    // TODO
    private static final long PING_PERIOD = 5000;

    private static SSLSocketFactory getSslSocketFactory(Context context) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            try {
                sslContext.init(
                        ClientKey.getInstance(context).getKeyManagers(),
                        ServerTrust.getInstance(context).getTrustManagers(),
                        null
                );
            } catch (KeyManagementException e) {
                throw new Error(e);
            }
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Runnable pingRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                webSocket.sendPing(null);
                handler.postDelayed(this, PING_PERIOD);
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
            }
        }

    };

    private final ConnectionStateListener connectionStateListener;
    private final NotificationListener notificationListener;
    private final OkHttpClient client;

    private Handler handler;
    private WebSocketCall webSocketCall;
    private WebSocket webSocket;

    private final SparseArray<JsonRpcRequestMessage> pendingRequests = new SparseArray<>();
    private final SparseArray<ResponseCallback> pendingRequestResponseCallbacks = new SparseArray<>();

    private int lastCommandIdentifier;

    public ServerConnection(Context context,
                            ConnectionStateListener connectionStateListener,
                            NotificationListener notificationListener) {

        // TODO
//        try {
//            ProviderInstaller.installIfNeeded(context);
//        } catch (GooglePlayServicesRepairableException e) {
//            GooglePlayServicesUtil.showErrorNotification(
//                    e.getConnectionStatusCode(),
//                    context
//            );
//            throw new IOException(e);
//        } catch (GooglePlayServicesNotAvailableException e) {
//            throw new IOException(e);
//        }

        this.connectionStateListener = connectionStateListener;
        this.notificationListener = notificationListener;
        this.client = new OkHttpClient();
        this.client.setHostnameVerifier(ServerTrust.getInstance(context).getHostnameVerifier());
        this.client.setSslSocketFactory(getSslSocketFactory(context));
    }

    public void connect() {
        log.debug("Creating websocket call");
        if (handler != null) {
            throw new IllegalStateException("Already connecting/connected");
        }
        HandlerThread handlerThread = new HandlerThread("ServerConnection");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                connectionStateListener.onStateChanged(ConnectionState.CONNECTING);
                webSocketCall = WebSocketCall.create(
                        client,
                        new com.squareup.okhttp.Request.Builder()
                                .url(SERVER_ENDPOINT)
                                .build()
                );
                webSocketCall.enqueue(ServerConnection.this);
            }

        });
    }

    public void disconnect() {
        disconnect(1000, "Bye");
    }

    private void disconnect(final int code, final String reason) {
        if (handler == null) {
            throw new IllegalStateException("Not connecting/connected");
        }
        handler.post(new Runnable() {

            @Override
            public void run() {
                connectionStateListener.onStateChanged(ConnectionState.DISCONNECTING);
                if (webSocket == null) {
                    log.debug("Cancelling websocket call");
                    webSocketCall.cancel();
                    webSocketCall = null;
                } else {
                    try {
                        log.debug("Closing websocket");
                        webSocket.close(code, reason);
                        webSocket = null;
                    } catch (IOException e) {
                        log.warn(e.getMessage(), e);
                    }
                }
            }

        });
    }

    @Override
    public void onClose(int code, String reason) {
        log.debug("WebSocket closed. Code: {}, reason: {}", code, reason);
        handler.post(new Runnable() {

            @Override
            public void run() {
                handler.removeCallbacks(pingRunnable);
                connectionStateListener.onStateChanged(ConnectionState.DISCONNECTED);
                webSocketCall = null;
                webSocket = null;
                handler.getLooper().quit();
                handler = null;
            }

        });
    }

    @Override
    public void onFailure(IOException e) {
        log.warn(e.getMessage(), e);
    }

    @Override
    public void onMessage(BufferedSource payload, WebSocket.PayloadType type) throws IOException {
        switch (type) {
            case TEXT:

                try {

                    JsResult<JsonRpcMessage> jsonRpcMessageJsResult = Json.fromJson(
                            Json.parse(payload.readUtf8()),
                            JsonRpcMessage$.MODULE$.JsonRpcMessageFormat()
                    );

                    if (jsonRpcMessageJsResult.isError()) {

                        log.warn("Invalid JSON-RPC message: {}", jsonRpcMessageJsResult);

                    } else {

                        JsonRpcMessage jsonRpcMessage = jsonRpcMessageJsResult.get();

                        if (jsonRpcMessage instanceof JsonRpcResponseMessage) {

                            final JsonRpcResponseMessage jsonRpcResponseMessage = (JsonRpcResponseMessage) jsonRpcMessage;

                            log.debug("Received JSON-RPC response message: {}", jsonRpcResponseMessage);

                            if (jsonRpcResponseMessage.id().isEmpty()) {

                                JsonRpcResponseError jsonRpcResponseError = jsonRpcResponseMessage.eitherErrorOrResult().left().get();

                                log.warn("Received JSON-RPC response error: {}", jsonRpcResponseError);

                            } else {

                                handler.post(new Runnable() {

                                    @Override
                                    public void run() {

                                        int commandIdentifier = Int.unbox((jsonRpcResponseMessage.id().get().right().get()));
                                        JsonRpcRequestMessage jsonRpcRequestMessage = pendingRequests.get(commandIdentifier);
                                        pendingRequests.remove(commandIdentifier);
                                        ResponseCallback responseCallback = pendingRequestResponseCallbacks.get(commandIdentifier);
                                        pendingRequestResponseCallbacks.remove(commandIdentifier);

                                        JsResult<Response> responseJsResult = Response$.MODULE$.read(
                                                jsonRpcResponseMessage,
                                                jsonRpcRequestMessage.method()
                                        );

                                        if (responseJsResult.isError()) {

                                            log.warn("Invalid Response: {}", responseJsResult);

                                        } else {

                                            Response response = responseJsResult.get();

                                            if (response instanceof ErrorResponse) {

                                                log.warn("Received response error: {}", response);

                                                responseCallback.onErrorReceived(
                                                        (ErrorResponse) response
                                                );

                                            } else {

                                                responseCallback.onResultReceived(
                                                        (ResultResponse) response
                                                );

                                            }

                                        }
                                    }

                                });

                            }

                        } else if (jsonRpcMessage instanceof JsonRpcNotificationMessage) {

                            JsonRpcNotificationMessage jsonRpcNotificationMessage = (JsonRpcNotificationMessage) jsonRpcMessage;

                            log.debug("Received JSON-RPC notification message: {}", jsonRpcNotificationMessage);

                            Option<JsResult<Notification>> maybeJsResultNotification = Notification$.MODULE$.read(
                                    jsonRpcNotificationMessage
                            );

                            if (maybeJsResultNotification.isEmpty()) {

                                log.warn("Received notification for unimplemented method");

                            } else {

                                final JsResult<Notification> jsResultNotification = maybeJsResultNotification.get();

                                if (jsResultNotification.isError()) {

                                    log.warn("Invalid Notification: {}", jsResultNotification);

                                } else {

                                    handler.post(new Runnable() {

                                        @Override
                                        public void run() {
                                            notificationListener.onNotificationReceived(
                                                    jsResultNotification.get()
                                            );
                                        }

                                    });

                                }

                            }

                        }

                    }

                } catch (JsonParseException e) {
                    log.warn("Invalid JSON: {}", e);
                }

                break;
            case BINARY:

               /*
                * See https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent.
                */
                disconnect(1003, "I don't support binary data");

                break;
            default:
                throw new IllegalStateException("Unknown payload type: " + type);
        }
        payload.close();
    }

    @Override
    public void onOpen(final WebSocket webSocket,
                       com.squareup.okhttp.Request request,
                       com.squareup.okhttp.Response response) throws IOException {
        log.debug("WebSocket opened");
        handler.post(new Runnable() {

            @Override
            public void run() {
                ServerConnection.this.webSocketCall = null;
                ServerConnection.this.webSocket = webSocket;
                connectionStateListener.onStateChanged(ConnectionState.CONNECTED);
                handler.postDelayed(pingRunnable, PING_PERIOD);
            }

        });
    }

    @Override
    public void onPong(Buffer payload) {
        String payloadString = null;
        if (payload != null) {
            payloadString = payload.readUtf8();
            payload.close();
        }
        log.debug("Received pong: {}", payloadString);
    }

    public void sendCommand(final Command command, final ResponseCallback responseCallback) {
        log.debug("Sending command: {}", command);
        handler.post(new Runnable() {

            @Override
            public void run() {
                if (webSocket == null) {
                    throw new IllegalStateException("Not connected");
                }
                try {
                    final int commandIdentifier = lastCommandIdentifier++;
                    final JsonRpcRequestMessage jsonRpcRequestMessage = Command$.MODULE$.write(
                            command,
                            Right.<String, Object>apply(Int.box(commandIdentifier))
                    );
                    webSocket.sendMessage(
                            WebSocket.PayloadType.TEXT,
                            new Buffer().writeUtf8(
                                    Json.stringify(
                                            Json.toJson(
                                                    jsonRpcRequestMessage,
                                                    JsonRpcRequestMessage.JsonRpcRequestMessageFormat()
                                            )
                                    )
                            )
                    );
                    pendingRequests.append(commandIdentifier, jsonRpcRequestMessage);
                    pendingRequestResponseCallbacks.append(commandIdentifier, responseCallback);
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }

        });
    }

}
