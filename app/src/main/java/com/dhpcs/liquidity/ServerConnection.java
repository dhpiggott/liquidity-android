package com.dhpcs.liquidity;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.dhpcs.liquidity.models.Command;
import com.dhpcs.liquidity.models.Command$;
import com.dhpcs.liquidity.models.Event;
import com.dhpcs.liquidity.models.Event$;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import de.greenrobot.event.EventBus;
import okio.Buffer;
import okio.BufferedSource;
import play.api.libs.json.JsResultException;
import play.api.libs.json.Json;

public class ServerConnection implements WebSocketListener {

    public enum ServerConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    private static final String SERVER_ENDPOINT = "https://liquidity.dhpcs.com/socket";

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

    private final OkHttpClient client;

    private Handler handler;
    private WebSocketCall webSocketCall;
    private WebSocket webSocket;

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
                EventBus.getDefault().postSticky(ServerConnectionState.CONNECTING);
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
                EventBus.getDefault().postSticky(ServerConnectionState.DISCONNECTING);
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

    private void execute(final Command command) {
        log.debug("Sending command message: {}", command);
        handler.post(new Runnable() {

            @Override
            public void run() {
                if (webSocket == null) {
                    throw new IllegalStateException("Not connected");
                }
                try {
                    webSocket.sendMessage(
                            WebSocket.PayloadType.TEXT,
                            new Buffer().writeUtf8(Json.stringify(Json.toJson(
                                    command,
                                    Command$.MODULE$.commandWrites()
                            ))));
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
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
                EventBus.getDefault().postSticky(ServerConnectionState.DISCONNECTED);
                EventBus.getDefault().unregister(ServerConnection.this);
                webSocketCall = null;
                webSocket = null;
                handler.getLooper().quit();
                handler = null;
            }

        });
    }

    // TODO
    public void onEvent(Command command) {
        execute(command);
    }

    @Override
    public void onFailure(IOException e) {
        log.warn(e.getMessage(), e);
        handler.post(new Runnable() {

            @Override
            public void run() {
                EventBus.getDefault().postSticky(ServerConnectionState.DISCONNECTED);
                EventBus.getDefault().unregister(ServerConnection.this);
                webSocketCall = null;
                webSocket = null;
                handler.getLooper().quit();
                handler = null;
            }

        });
    }

    @Override
    public void onMessage(BufferedSource payload, WebSocket.PayloadType type) throws IOException {
        switch (type) {
            case TEXT:

                try {
                    final Event event = Json.parse(payload.readUtf8())
                            .as(Event$.MODULE$.eventReads());

                    log.debug("Event message received: {}", event);

                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            EventBus.getDefault().post(event);
                        }

                    });

                } catch (JsResultException e) {
                    throw new IOException(e);
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
                EventBus.getDefault().register(ServerConnection.this);
                EventBus.getDefault().postSticky(ServerConnectionState.CONNECTED);
            }

        });
    }

    @Override
    public void onPong(Buffer payload) {
        log.debug("Received pong: {}", payload.readUtf8());
    }

    // TODO
    private void ping() {
        handler.post(new Runnable() {

            @Override
            public void run() {
                try {
                    webSocket.sendPing(null);
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }

        });
    }

}
