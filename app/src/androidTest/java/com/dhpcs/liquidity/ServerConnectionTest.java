package com.dhpcs.liquidity;

import android.test.AndroidTestCase;

import com.dhpcs.liquidity.models.CreateZone;
import com.dhpcs.liquidity.models.Event;
import com.dhpcs.liquidity.models.ZoneCreated;
import com.dhpcs.liquidity.models.ZoneState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.greenrobot.event.EventBus;

public class ServerConnectionTest extends AndroidTestCase {

    private final Logger log = LoggerFactory.getLogger(ServerConnectionTest.class);

    private final CyclicBarrier eventSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier eventReadBarrier = new CyclicBarrier(2);
    private final CyclicBarrier serverConnectionStateSetBarrier = new CyclicBarrier(2);
    private final CyclicBarrier serverConnectionStateReadBarrier = new CyclicBarrier(2);

    private Event event;
    private ServerConnection.ServerConnectionState serverConnectionState;

    public void onEvent(Event event) throws InterruptedException, BrokenBarrierException {
        log.debug("event={}", event);
        eventReadBarrier.await();
        this.event = event;
        eventSetBarrier.await();
    }

    public void onEvent(ServerConnection.ServerConnectionState serverConnectionState) throws BrokenBarrierException, InterruptedException {
        log.debug("serverConnectionState={}", serverConnectionState);
        serverConnectionStateReadBarrier.await();
        this.serverConnectionState = serverConnectionState;
        serverConnectionStateSetBarrier.await();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        EventBus.getDefault().registerSticky(this);
    }

    @Override
    public void tearDown() throws Exception {
        EventBus.getDefault().unregister(this);
        super.tearDown();
    }

    public void testStream() throws InterruptedException, BrokenBarrierException, TimeoutException {

        ServerConnection.getInstance(getContext()).connect();
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.CONNECTING, serverConnectionState);
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.CONNECTED, serverConnectionState);

        EventBus.getDefault().post(new CreateZone("Dave's zone", GameType.TEST.typeName));
        eventReadBarrier.await(15, TimeUnit.SECONDS);
        eventSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(event instanceof ZoneCreated);
        eventReadBarrier.await(15, TimeUnit.SECONDS);
        eventSetBarrier.await(15, TimeUnit.SECONDS);
        assertTrue(event instanceof ZoneState);
        assertEquals(GameType.TEST.typeName, ((ZoneState) event).zone().zoneType());

        ServerConnection.getInstance(getContext()).disconnect();
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.DISCONNECTING, serverConnectionState);
        serverConnectionStateReadBarrier.await(15, TimeUnit.SECONDS);
        serverConnectionStateSetBarrier.await(15, TimeUnit.SECONDS);
        assertEquals(ServerConnection.ServerConnectionState.DISCONNECTED, serverConnectionState);

    }

}
