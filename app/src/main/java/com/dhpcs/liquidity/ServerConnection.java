package com.dhpcs.liquidity;

import android.content.Context;
import android.util.Log;

import com.dhpcs.liquidity.models.Command;
import com.dhpcs.liquidity.models.ConnectionNumber;
import com.dhpcs.liquidity.models.Event;
import com.dhpcs.liquidity.models.Event$;
import com.dhpcs.liquidity.models.Heartbeat;
import com.dhpcs.liquidity.models.PostedCommand;
import com.dhpcs.liquidity.models.PostedCommand$;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okio.Buffer;
import okio.BufferedSource;
import play.api.libs.json.JsResult;
import play.api.libs.json.Json;

public class ServerConnection {

    private static final String SERVER_ENDPOINT = "https://liquidity.dhpcs.com/action";

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

    private final OkHttpClient client;

    private BufferedSource source;
    private int serverConnectionNumber;

    public ServerConnection(Context context) {

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

    public void connect() throws IOException {
        if(source != null) {
         throw new IllegalStateException("Already connected");
        } else {
            source = client.newCall(
                    new Request.Builder()
                            .url(SERVER_ENDPOINT)
                            .get().build()
            ).execute().body().source();
            serverConnectionNumber = ((ConnectionNumber) read()).connectionNumber();
        }
    }

    public void disconnect() throws IOException {
        if(source == null) {
            throw new IllegalStateException("Not connected");
        } else {
            source.close();
            source = null;
        }
    }

    public void post(Command command) throws IOException {
        if(source != null) {
            throw new IllegalStateException("Already connected");
        } else {
            client.newCall(
                    new Request.Builder()
                            .url(SERVER_ENDPOINT)
                            .post(
                                    RequestBody.create(
                                            MediaType.parse("application/json"),
                                            Json.toJson(
                                                    new PostedCommand(
                                                            serverConnectionNumber,
                                                            command),
                                                    PostedCommand$.MODULE$.postedCommandFormat()
                                            ).toString()
                                    )
                            )
                            .build()
            ).execute().body().source();
        }
    }

    public Event read() throws IOException {
        // TODO
        String sseEventString = source.readUtf8LineStrict();
        Log.d(getClass().getSimpleName(), sseEventString);
        source.readUtf8LineStrict();
        String eventString = sseEventString.replaceFirst("data: ", "");
        JsResult<Event> event = Json.parse(eventString)
                .validate(Event$.MODULE$.eventReads());
        if (event.isError()) {
            throw new IOException();
        }
        if (!(event.get() instanceof Heartbeat)) {
            return event.get();
        } else {
            return read();
        }
    }

}
