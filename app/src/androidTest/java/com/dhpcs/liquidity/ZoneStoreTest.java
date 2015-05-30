package com.dhpcs.liquidity;

import android.test.AndroidTestCase;

import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.ZoneId;

import java.util.Iterator;

public class ZoneStoreTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ZoneStore.deleteAll(getContext());
    }

    public void testIterator() {

        ZoneId zoneId = (ZoneId) ZoneId.generate();
        Zone zone = Zone.apply(
                "Dave's zone",
                GameType.TEST.typeName
        );
        new ZoneStore(getContext(), GameType.TEST.typeName, zoneId).save(zone);

        Iterator<ZoneStore> zoneStateIterator = new ZoneStore.ZoneStoreIterator(getContext(), GameType.TEST);

        assertEquals(true, zoneStateIterator.hasNext());
        assertEquals(zone, zoneStateIterator.next().load());
        assertEquals(false, zoneStateIterator.hasNext());

    }

    public void testLoad() {

        ZoneId zoneId = (ZoneId) ZoneId.generate();
        Zone zone = Zone.apply(
                "Dave's zone",
                GameType.TEST.typeName
        );
        new ZoneStore(getContext(), GameType.TEST.typeName, zoneId).save(zone);

        Zone loadedZone = new ZoneStore(getContext(), GameType.TEST.typeName, zoneId).load();

        assertEquals(zone, loadedZone);

    }

}
