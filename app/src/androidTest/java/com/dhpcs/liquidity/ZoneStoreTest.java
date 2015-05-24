package com.dhpcs.liquidity;

import android.test.AndroidTestCase;

import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.models.ZoneState;

import java.util.Iterator;

public class ZoneStoreTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ZoneStore.deleteAll(getContext());
    }

    public void testIterator() {

        ZoneId zoneId = ZoneId.apply();
        ZoneState zoneState =
                new ZoneState(
                        zoneId,
                        Zone.apply(
                                "Dave's zone",
                                GameType.TEST.typeName
                        )
                );
        new ZoneStore(getContext(), GameType.TEST.typeName, zoneId).saveState(zoneState);

        Iterator<ZoneStore> zoneStateIterator = new ZoneStore.ZoneStoreIterator(getContext(), GameType.TEST);

        assertEquals(true, zoneStateIterator.hasNext());
        assertEquals(zoneState, zoneStateIterator.next().loadState());
        assertEquals(false, zoneStateIterator.hasNext());

    }

    public void testLoadState() {

        ZoneId zoneId = ZoneId.apply();
        ZoneState zoneState =
                new ZoneState(
                        zoneId,
                        Zone.apply(
                                "Dave's zone",
                                 GameType.TEST.typeName
                        )
                );
        new ZoneStore(getContext(), GameType.TEST.typeName, zoneId).saveState(zoneState);

        ZoneState loadedZoneState = new ZoneStore(getContext(), GameType.TEST.typeName, zoneId).loadState();

        assertEquals(zoneState, loadedZoneState);

    }

}
