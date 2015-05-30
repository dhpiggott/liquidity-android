package com.dhpcs.liquidity;

import android.test.AndroidTestCase;

import com.dhpcs.liquidity.models.CommandResponse;
import com.dhpcs.liquidity.models.CreateZone;
import com.dhpcs.liquidity.models.Notification;
import com.dhpcs.liquidity.models.ZoneCreated;
import com.dhpcs.liquidity.models.ZoneState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerConnectionTest extends AndroidTestCase implements ServerConnection.Listener {

    private final Logger log = LoggerFactory.getLogger(ServerConnectionTest.class);

    private final CyclicBarrier commandResponseSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier commandResponseReadBarrier = new CyclicBarrier(2);
    private final CyclicBarrier notificationSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier notificationReadBarrier = new CyclicBarrier(2);
    private final CyclicBarrier serverConnectionStateSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier serverConnectionStateReadBarrier = new CyclicBarrier(2);

    private CommandResponse commandResponse;
    private Notification notification;
    private ServerConnection.ServerConnectionState serverConnectionState;

    @Override
    public void onNotificationReceived(Notification notification) {
        try {
            log.debug("notification={}", notification);
            notificationReadBarrier.await();
            this.notification = notification;
            notificationSetBarrier.await();
        } catch (InterruptedException
                | BrokenBarrierException e) {
            throw new Error(e);
        }
    }

    @Override
    public void onStateChanged(ServerConnection.ServerConnectionState serverConnectionState) {
        try {
            log.debug("serverConnectionState={}", serverConnectionState);
            serverConnectionStateReadBarrier.await();
            this.serverConnectionState = serverConnectionState;
            serverConnectionStateSetBarrier.await();
        } catch (InterruptedException
                | BrokenBarrierException e) {
            throw new Error(e);
        }
    }

    public void testStream() throws InterruptedException, BrokenBarrierException, TimeoutException {
        ServerConnection.getInstance(getContext()).addListener(this);

        ServerConnection.getInstance(getContext()).connect();
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.CONNECTING, serverConnectionState);
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.CONNECTED, serverConnectionState);

        ServerConnection.getInstance(getContext()).sendCommand(new CreateZone("Dave's zone", GameType.TEST.typeName), new ServerConnection.CommandResponseCallback() {
            @Override
            public void onCommandResponseReceived(CommandResponse commandResponse) {
                try {
                    log.debug("commandResponse={}", commandResponse);
                    commandResponseReadBarrier.await();
                    ServerConnectionTest.this.commandResponse = commandResponse;
                    commandResponseSetBarrier.await();
                } catch (InterruptedException
                        | BrokenBarrierException e) {
                    throw new Error(e);
                }
            }
        });
        commandResponseReadBarrier.await(15, TimeUnit.SECONDS);
        commandResponseSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(commandResponse instanceof ZoneCreated);
        notificationReadBarrier.await(15, TimeUnit.SECONDS);
        notificationSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(notification instanceof ZoneState);
        assertEquals(GameType.TEST.typeName, ((ZoneState) notification).zone().zoneType());

        ServerConnection.getInstance(getContext()).disconnect();
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.DISCONNECTING, serverConnectionState);
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.DISCONNECTED, serverConnectionState);

        ServerConnection.getInstance(getContext()).removeListener(this);
    }

}
