package com.dhpcs.liquidity;

import android.test.AndroidTestCase;

import com.dhpcs.liquidity.models.ClientJoinedZoneNotification;
import com.dhpcs.liquidity.models.CreateZoneCommand;
import com.dhpcs.liquidity.models.CreateZoneResponse;
import com.dhpcs.liquidity.models.JoinZoneCommand;
import com.dhpcs.liquidity.models.JoinZoneResponse;
import com.dhpcs.liquidity.models.Notification;
import com.dhpcs.liquidity.models.ResultResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerConnectionTest extends AndroidTestCase
        implements ServerConnection.ConnectionStateListener,
        ServerConnection.NotificationListener {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CyclicBarrier commandResultResponseSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier commandResultResponseReadBarrier = new CyclicBarrier(2);
    private final CyclicBarrier notificationSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier notificationReadBarrier = new CyclicBarrier(2);
    private final CyclicBarrier serverConnectionStateSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier serverConnectionStateReadBarrier = new CyclicBarrier(2);

    private ResultResponse resultResponse;
    private Notification notification;
    private ServerConnection.ConnectionState connectionState;

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
    public void onStateChanged(ServerConnection.ConnectionState connectionState) {
        try {
            log.debug("connectionState={}", connectionState);
            serverConnectionStateReadBarrier.await();
            this.connectionState = connectionState;
            serverConnectionStateSetBarrier.await();
        } catch (InterruptedException
                | BrokenBarrierException e) {
            throw new Error(e);
        }
    }

    public void testStream() throws InterruptedException, BrokenBarrierException, TimeoutException {
        ServerConnection serverConnection = new ServerConnection(getContext(), this, this);

        serverConnection.connect();
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ConnectionState.CONNECTING, connectionState);
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ConnectionState.CONNECTED, connectionState);

        serverConnection.sendCommand(new CreateZoneCommand("Dave's zone", GameType.TEST.typeName), new ServerConnection.ResponseCallback() {

            @Override
            public void onResultReceived(ResultResponse resultResponse) {
                try {
                    log.debug("resultResponse={}", resultResponse);
                    commandResultResponseReadBarrier.await();
                    ServerConnectionTest.this.resultResponse = resultResponse;
                    commandResultResponseSetBarrier.await();
                } catch (InterruptedException
                        | BrokenBarrierException e) {
                    throw new Error(e);
                }
            }

        });
        commandResultResponseReadBarrier.await(15, TimeUnit.SECONDS);
        commandResultResponseSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(resultResponse instanceof CreateZoneResponse);

        serverConnection.sendCommand(new JoinZoneCommand(((CreateZoneResponse) resultResponse).zoneId()), new ServerConnection.ResponseCallback() {

            @Override
            public void onResultReceived(ResultResponse resultResponse) {
                try {
                    log.debug("resultResponse={}", resultResponse);
                    commandResultResponseReadBarrier.await();
                    ServerConnectionTest.this.resultResponse = resultResponse;
                    commandResultResponseSetBarrier.await();
                } catch (InterruptedException
                        | BrokenBarrierException e) {
                    throw new Error(e);
                }
            }

        });
        commandResultResponseReadBarrier.await(15, TimeUnit.SECONDS);
        commandResultResponseSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(resultResponse instanceof JoinZoneResponse);
        notificationReadBarrier.await(15, TimeUnit.SECONDS);
        notificationSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(notification instanceof ClientJoinedZoneNotification);

        serverConnection.disconnect();
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ConnectionState.DISCONNECTING, connectionState);
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ConnectionState.DISCONNECTED, connectionState);
    }

}
