package com.dhpcs.liquidity;

import android.content.Context;

import com.dhpcs.liquidity.models.Event;
import com.dhpcs.liquidity.models.Event$;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.models.ZoneState;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import play.api.libs.json.JsResult;
import play.api.libs.json.Json;

public class ZoneStore {

    private static final String ZONES_DIRECTORY_NAME = "zones";

    public static class ZoneStoreIterator implements Iterator<ZoneStore> {

        private final Context context;
        private final String[] storedZoneIds;
        private int i;

        public ZoneStoreIterator(Context context) {
            this.context = context;
            this.storedZoneIds = new File(
                    context.getFilesDir(),
                    ZONES_DIRECTORY_NAME
            ).list();
        }

        @Override
        public boolean hasNext() {
            return i < storedZoneIds.length;
        }

        @Override
        public ZoneStore next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String nextZoneIdString = storedZoneIds[i++];
            return new ZoneStore(
                    context,
                    new ZoneId(
                            UUID.fromString(nextZoneIdString)
                    )
            );
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    public static void deleteAll(Context context) {
        File zonesDirectory =
                new File(
                        context.getFilesDir(),
                        ZONES_DIRECTORY_NAME
                );
        if (zonesDirectory.exists()) {
            try {
                FileUtils.forceDelete(zonesDirectory);
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }

    private final File zonesDirectory;
    private final File zoneStateFile;

    public ZoneStore(Context context, ZoneId zoneId) {
        this.zonesDirectory = new File(
                new File(
                        context.getFilesDir(),
                        ZONES_DIRECTORY_NAME
                ),
                zoneId.id().toString()
        );
        this.zoneStateFile = new File(
                zonesDirectory,
                "zoneState.json"
        );
    }

    public void delete() {
        try {
            FileUtils.forceDelete(zonesDirectory);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public boolean hasState() {
        return zoneStateFile.exists();
    }

    // TODO: Versioning?
    public ZoneState loadState() {
        try {
            BufferedSource bufferedSource = Okio.buffer(Okio.source(zoneStateFile));
            try {
                try {
                    JsResult<Event> jsResult = Json.fromJson(
                            Json.parse(bufferedSource.readUtf8()),
                            Event$.MODULE$.eventReads()
                    );
                    return (ZoneState) jsResult.get();
                } finally {
                    bufferedSource.close();
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        } catch (FileNotFoundException e) {
            throw new Error(e);
        }
    }

    // TODO: Versioning?
    public void saveState(ZoneState zoneState) {
        try {
            FileUtils.forceMkdir(zoneStateFile.getParentFile());
        } catch (IOException e) {
            throw new Error(e);
        }
        try {
            BufferedSink bufferedSink = Okio.buffer(Okio.sink(zoneStateFile));
            try {
                try {
                    bufferedSink.writeUtf8(Json.prettyPrint(
                            Json.toJson(
                                    zoneState,
                                    Event$.MODULE$.eventWrites()
                            )
                    ));
                } finally {
                    bufferedSink.close();
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        } catch (FileNotFoundException e) {
            throw new Error(e);
        }
    }

}
