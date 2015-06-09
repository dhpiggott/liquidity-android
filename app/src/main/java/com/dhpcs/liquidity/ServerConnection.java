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
import com.dhpcs.liquidity.models.CommandErrorResponse;
import com.dhpcs.liquidity.models.CommandResponse;
import com.dhpcs.liquidity.models.CommandResponse$;
import com.dhpcs.liquidity.models.Notification;
import com.dhpcs.liquidity.models.Notification$;
import com.fasterxml.jackson.core.JsonParseException;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

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

    public interface CommandResponseCallback<T extends CommandResponse> {

        void onResponseReceived(T commandResponse);

        void onErrorReceived(CommandErrorResponse commandErrorResponse);

        // TODO: Timeouts?

    }

    public interface Listener {

        void onNotificationReceived(Notification notification);

        void onStateChanged(ServerConnectionState serverConnectionState);

    }

    public enum ServerConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    private static final String SERVER_ENDPOINT = "https://liquidity.dhpcs.com/ws";

    private static volatile ServerConnection instance;

    public static ServerConnection getInstance(Context context) {
        if (instance == null) {
            synchronized (ServerConnection.class) {
                if (instance == null) {
                    instance = new ServerConnection(context);
                }
            }
        }
        return instance;
    }

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

    private final Logger log = LoggerFactory.getLogger(ServerConnection.class);

    // TODO: Do we need to support multiple listeners? Or should we support multiple instances but
    // only allow one listener per instance?
    private final Set<Listener> listeners = new HashSet<>();

    private final OkHttpClient client;

    private ServerConnectionState serverConnectionState;
    private Handler handler;
    private WebSocketCall webSocketCall;
    private WebSocket webSocket;

    private final SparseArray<JsonRpcRequestMessage> pendingRequests = new SparseArray<>();
    private final SparseArray<CommandResponseCallback> pendingRequestCommandResponseCallbacks = new SparseArray<>();
    private int lastCommandIdentifier;

    private ServerConnection(Context context) {

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

        this.client = new OkHttpClient();
        this.client.setHostnameVerifier(ServerTrust.getInstance(context).getHostnameVerifier());
        this.client.setSslSocketFactory(getSslSocketFactory(context));
    }

    public void addListener(Listener listener) {
        if (serverConnectionState != null) {
            listener.onStateChanged(serverConnectionState);
        }
        listeners.add(listener);
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
                setState(ServerConnectionState.CONNECTING);
                webSocketCall = WebSocketCall.create(
                        client, new Request.Builder()
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
                setState(ServerConnectionState.DISCONNECTING);
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
                setState(ServerConnectionState.DISCONNECTED);
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
                                        CommandResponseCallback commandResponseCallback = pendingRequestCommandResponseCallbacks.get(commandIdentifier);
                                        pendingRequestCommandResponseCallbacks.remove(commandIdentifier);

                                        JsResult<CommandResponse> jsResultCommandResponse = CommandResponse$.MODULE$.readCommandResponse(
                                                jsonRpcResponseMessage,
                                                jsonRpcRequestMessage.method()
                                        );

                                        if (jsResultCommandResponse.isError()) {

                                            log.warn("Invalid CommandResponse: {}", jsResultCommandResponse);

                                        } else {

                                            CommandResponse commandResponse = jsResultCommandResponse.get();

                                            if (commandResponse instanceof CommandErrorResponse) {

                                                log.warn("Received command response error: {}", jsResultCommandResponse.get());

                                                commandResponseCallback.onErrorReceived(
                                                        (CommandErrorResponse) commandResponse
                                                );

                                            } else {

                                                commandResponseCallback.onResponseReceived(
                                                        commandResponse
                                                );

                                            }

                                        }
                                    }

                                });

                            }

                        } else if (jsonRpcMessage instanceof JsonRpcNotificationMessage) {

                            JsonRpcNotificationMessage jsonRpcNotificationMessage = (JsonRpcNotificationMessage) jsonRpcMessage;

                            log.debug("Received JSON-RPC notification message: {}", jsonRpcNotificationMessage);

                            Option<JsResult<Notification>> maybeJsResultNotification = Notification$.MODULE$.readNotification(
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
                                            for (Listener listener : listeners) {
                                                listener.onNotificationReceived(
                                                        jsResultNotification.get()
                                                );
                                            }
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
    public void onOpen(final WebSocket webSocket, Request request, Response response)
            throws IOException {
        log.debug("WebSocket opened");
        handler.post(new Runnable() {

            @Override
            public void run() {
                ServerConnection.this.webSocketCall = null;
                ServerConnection.this.webSocket = webSocket;
                setState(ServerConnectionState.CONNECTED);
            }

        });
    }

    @Override
    public void onPong(Buffer payload) {
        log.debug("Received pong: {}", payload.readUtf8());
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void sendCommand(final Command command, final CommandResponseCallback commandResponseCallback) {
        log.debug("Sending command: {}", command);
        handler.post(new Runnable() {

            @Override
            public void run() {
                if (webSocket == null) {
                    throw new IllegalStateException("Not connected");
                }
                try {
                    final int commandIdentifier = lastCommandIdentifier++;
                    final JsonRpcRequestMessage jsonRpcRequestMessage = Command$.MODULE$.writeCommand(
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
                    pendingRequestCommandResponseCallbacks.append(commandIdentifier, commandResponseCallback);
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }

        });
    }

    private void setState(ServerConnectionState serverConnectionState) {
        this.serverConnectionState = serverConnectionState;
        for (Listener listener : listeners) {
            listener.onStateChanged(serverConnectionState);
        }
    }

}
