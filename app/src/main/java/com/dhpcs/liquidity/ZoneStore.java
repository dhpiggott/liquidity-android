package com.dhpcs.liquidity;

import android.content.Context;

import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.ZoneId;

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

// TODO: Rewrite using database and only storing subset of info
public class ZoneStore {

    private static final String ZONES_DIRECTORY_NAME = "zones";

    public static class ZoneStoreIterator implements Iterator<ZoneStore> {

        private final Context context;
        private final GameType gameType;
        private final String[] storedZoneIds;
        private int i;

        public ZoneStoreIterator(Context context, GameType gameType) {
            this.context = context;
            this.gameType = gameType;
            this.storedZoneIds = new File(
                    new File(
                            context.getFilesDir(),
                            ZONES_DIRECTORY_NAME),
                    gameType.typeName
            ).list();
        }

        @Override
        public boolean hasNext() {
            return storedZoneIds != null && i < storedZoneIds.length;
        }

        @Override
        public ZoneStore next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String nextZoneIdString = storedZoneIds[i++];
            return new ZoneStore(
                    context,
                    gameType.typeName,
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

    private final ZoneId zoneId;
    private final File zoneDirectory;
    private final File zoneFile;

    public ZoneStore(Context context, String zoneType, ZoneId zoneId) {
        this.zoneId = zoneId;
        this.zoneDirectory = new File(
                new File(
                        new File(
                                context.getFilesDir(),
                                ZONES_DIRECTORY_NAME
                        ),
                        zoneType),
                zoneId.id().toString()
        );
        this.zoneFile = new File(
                zoneDirectory,
                "zone.json"
        );
    }

    public void delete() {
        try {
            FileUtils.forceDelete(zoneDirectory);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public Zone load() {
        try {
            BufferedSource bufferedSource = Okio.buffer(Okio.source(zoneFile));
            try {
                try {
                    JsResult<Zone> jsResult = Json.fromJson(
                            Json.parse(bufferedSource.readUtf8()),
                            Zone.ZoneFormat()
                    );
                    return jsResult.get();
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

    public void save(Zone zone) {
        try {
            FileUtils.forceMkdir(zoneDirectory);
        } catch (IOException e) {
            throw new Error(e);
        }
        try {
            BufferedSink bufferedSink = Okio.buffer(Okio.sink(zoneFile));
            try {
                try {
                    bufferedSink.writeUtf8(Json.prettyPrint(
                            Json.toJson(
                                    zone,
                                    Zone.ZoneFormat()
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

    @Override
    public String toString() {
        return load().name();
    }

}
