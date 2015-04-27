package com.dhpcs.liquidity;

import android.test.AndroidTestCase;
import android.util.Log;

import java.io.IOException;

import com.dhpcs.liquidity.models.CreateZone;
import com.dhpcs.liquidity.models.Event;
import com.dhpcs.liquidity.models.ZoneCreated;
import com.dhpcs.liquidity.models.ZoneState;

public class ServerConnectionTest extends AndroidTestCase {

    public void testStream() throws IOException {

        ServerConnection serverConnection = new ServerConnection(getContext());
        serverConnection.connect();

        serverConnection.post(new CreateZone("Dave's zone", "test"));

        Event zoneCreated = serverConnection.read();
        Log.d(getClass().getSimpleName(), zoneCreated.getClass().getCanonicalName());
        boolean isZoneCreated = zoneCreated instanceof ZoneCreated;
        assertTrue(isZoneCreated);

        Event zoneState = serverConnection.read();
        boolean isZoneState = zoneState instanceof ZoneState;
        assertTrue(isZoneState);

        Log.d(getClass().getSimpleName(), zoneState.toString());

        assertEquals("Dave's zone", ((ZoneState)zoneState).zone().name());
        assertEquals("test", ((ZoneState)zoneState).zone().zoneType());

        serverConnection.disconnect();

    }

}
