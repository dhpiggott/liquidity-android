package com.dhpcs.liquidity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.support.multidex.MultiDexApplication;

import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.provider.LiquidityContract;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Interval;
import org.joda.time.Minutes;
import org.joda.time.ReadableInstant;
import org.joda.time.Seconds;
import org.joda.time.Weeks;

import java.io.InputStream;

public class LiquidityApplication extends MultiDexApplication {

    private static BoardGame.GameDatabase gameDatabase;

    public static BoardGame.GameDatabase getGameDatabase(final Context context) {
        if (gameDatabase == null) {
            gameDatabase = new BoardGame.GameDatabase() {

                // TODO: Update expires if changed
                @Override
                public Long checkAndUpdateGame(ZoneId zoneId, long expires, String name) {
                    Cursor existingEntry =
                            context.getContentResolver().query(
                                    LiquidityContract.Games.CONTENT_URI,
                                    new String[]{
                                            LiquidityContract.Games._ID,
                                            LiquidityContract.Games.NAME
                                    },
                                    LiquidityContract.Games.ZONE_ID + " = ?",
                                    new String[]{zoneId.id().toString()},
                                    null
                            );
                    if (existingEntry == null) {
                        return null;
                    } else {
                        try {
                            if (!existingEntry.moveToFirst()) {
                                return null;
                            } else {
                                long gameId = existingEntry.getLong(
                                        existingEntry.getColumnIndexOrThrow(
                                                LiquidityContract.Games._ID
                                        )
                                );
                                if (!existingEntry.getString(
                                        existingEntry.getColumnIndexOrThrow(
                                                LiquidityContract.Games.NAME
                                        )
                                ).equals(name)) {
                                    updateGameName(gameId, name);
                                }
                                return gameId;
                            }
                        } finally {
                            existingEntry.close();
                        }
                    }
                }

                @Override
                public long insertGame(ZoneId zoneId, long created, long expires, String name) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id().toString());
                    contentValues.put(LiquidityContract.Games.CREATED, created);
                    contentValues.put(LiquidityContract.Games.EXPIRES, expires);
                    contentValues.put(LiquidityContract.Games.NAME, name);
                    return ContentUris.parseId(
                            context.getContentResolver().insert(
                                    LiquidityContract.Games.CONTENT_URI,
                                    contentValues
                            )
                    );
                }

                @Override
                public void updateGameName(long gameId, String name) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(LiquidityContract.Games.NAME, name);
                    context.getContentResolver().update(
                            ContentUris.withAppendedId(LiquidityContract.Games.CONTENT_URI, gameId),
                            contentValues,
                            null,
                            null
                    );
                }

            };
        }
        return gameDatabase;
    }

    public static String getRelativeTimeSpanString(Context context,
                                                   ReadableInstant time,
                                                   ReadableInstant now,
                                                   long minResolution) {
        int flags = android.text.format.DateUtils.FORMAT_SHOW_DATE
                | android.text.format.DateUtils.FORMAT_SHOW_YEAR |
                android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
        return getRelativeTimeSpanString(context, time, now, minResolution, flags);
    }

    @SuppressLint("PrivateResource")
    public static String getRelativeTimeSpanString(Context context,
                                                   ReadableInstant time,
                                                   ReadableInstant now,
                                                   long minResolution,
                                                   int flags) {
        boolean abbrevRelative = (flags
                & (android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                | android.text.format.DateUtils.FORMAT_ABBREV_ALL)) != 0;

        DateTime timeRounded = new DateTime(time).withMillisOfSecond(0);
        DateTime nowRounded = new DateTime(now).withMillisOfSecond(0);
        boolean past = !nowRounded.isBefore(timeRounded);
        Interval interval = past
                ?
                new Interval(timeRounded, nowRounded)
                :
                new Interval(nowRounded, timeRounded);

        int resId;
        long count;
        if (minResolution < android.text.format.DateUtils.MINUTE_IN_MILLIS
                && Minutes.minutesIn(interval).isLessThan(Minutes.ONE)) {
            count = Seconds.secondsIn(interval).getSeconds();
            if (past) {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_num_seconds_ago;
                } else {
                    resId = R.plurals.joda_time_android_num_seconds_ago;
                }
            } else {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_in_num_seconds;
                } else {
                    resId = R.plurals.joda_time_android_in_num_seconds;
                }
            }
        } else if (minResolution < android.text.format.DateUtils.HOUR_IN_MILLIS
                && Hours.hoursIn(interval).isLessThan(Hours.ONE)) {
            count = Minutes.minutesIn(interval).getMinutes();
            if (past) {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_num_minutes_ago;
                } else {
                    resId = R.plurals.joda_time_android_num_minutes_ago;
                }
            } else {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_in_num_minutes;
                } else {
                    resId = R.plurals.joda_time_android_in_num_minutes;
                }
            }
        } else if (minResolution < android.text.format.DateUtils.DAY_IN_MILLIS
                && Days.daysIn(interval).isLessThan(Days.ONE)) {
            count = Hours.hoursIn(interval).getHours();
            if (past) {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_num_hours_ago;
                } else {
                    resId = R.plurals.joda_time_android_num_hours_ago;
                }
            } else {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_in_num_hours;
                } else {
                    resId = R.plurals.joda_time_android_in_num_hours;
                }
            }
        } else if (minResolution < android.text.format.DateUtils.WEEK_IN_MILLIS
                && (past ?
                Weeks.weeksBetween(timeRounded.toLocalDate(), nowRounded.toLocalDate())
                :
                Weeks.weeksBetween(nowRounded.toLocalDate(), timeRounded.toLocalDate()))
                .isLessThan(Weeks.ONE)) {
            count = past ?
                    Days.daysBetween(timeRounded.toLocalDate(), nowRounded.toLocalDate()).getDays()
                    :
                    Days.daysBetween(nowRounded.toLocalDate(), timeRounded.toLocalDate()).getDays();
            if (past) {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_num_days_ago;
                } else {
                    resId = R.plurals.joda_time_android_num_days_ago;
                }
            } else {
                if (abbrevRelative) {
                    resId = R.plurals.joda_time_android_abbrev_in_num_days;
                } else {
                    resId = R.plurals.joda_time_android_in_num_days;
                }
            }
        } else {
            return net.danlew.android.joda.DateUtils.formatDateRange(context, time, time, flags);
        }

        String format = context.getResources().getQuantityString(resId, (int) count);
        return String.format(format, count);
    }

    private static ServerConnection serverConnection;

    public static ServerConnection getServerConnection(final Context context) {
        if (serverConnection == null) {
            serverConnection = ServerConnection.getInstance(
                    new ServerConnection.PRNGFixesApplicator() {

                        @Override
                        public void apply() {
                            PRNGFixes.apply();
                        }

                    },
                    context.getFilesDir(),
                    Settings.Secure.getString(
                            context.getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    ),
                    new ServerConnection.KeyStoreInputStreamProvider() {

                        @Override
                        public InputStream get() {
                            return context.getResources().openRawResource(
                                    R.raw.liquidity_dhpcs_com
                            );
                        }

                    },
                    new ServerConnection.ConnectivityStatePublisherBuilder() {

                        @Override
                        public ServerConnection.ConnectivityStatePublisher build(
                                final ServerConnection serverConnection) {
                            return new ServerConnection.ConnectivityStatePublisher() {

                                private IntentFilter connectionStateFilter = new IntentFilter(
                                        android.net.ConnectivityManager.CONNECTIVITY_ACTION
                                );
                                private BroadcastReceiver connectionStateReceiver =
                                        new BroadcastReceiver() {

                                            @Override
                                            public void onReceive(Context context, Intent intent) {
                                                switch (intent.getAction()) {
                                                    case ConnectivityManager.CONNECTIVITY_ACTION:

                                                        serverConnection
                                                                .handleConnectivityStateChange();

                                                        break;
                                                    default:
                                                        throw new RuntimeException(
                                                                "Received unexpected broadcast " +
                                                                        "for action " +
                                                                        intent.getAction()
                                                        );
                                                }
                                            }

                                        };

                                @Override
                                public void register() {
                                    context.registerReceiver(
                                            connectionStateReceiver,
                                            connectionStateFilter
                                    );
                                }

                                @Override
                                public void unregister() {
                                    context.unregisterReceiver(
                                            connectionStateReceiver
                                    );
                                }

                                @Override
                                public boolean isConnectionAvailable() {
                                    NetworkInfo activeNetwork =
                                            ((android.net.ConnectivityManager)
                                                    context.getSystemService(
                                                            Context.CONNECTIVITY_SERVICE
                                                    )
                                            ).getActiveNetworkInfo();
                                    return activeNetwork != null && activeNetwork.isConnected();
                                }

                            };
                        }

                    },
                    new ServerConnection.HandlerWrapperFactory() {

                        @Override
                        public ServerConnection.HandlerWrapper create(String name) {
                            HandlerThread handlerThread = new HandlerThread(name);
                            handlerThread.start();
                            return wrap(handlerThread.getLooper());
                        }

                        @Override
                        public ServerConnection.HandlerWrapper main() {
                            return wrap(Looper.getMainLooper());
                        }

                        private ServerConnection.HandlerWrapper wrap(final Looper looper) {
                            return new ServerConnection.HandlerWrapper() {

                                private final Handler handler = new Handler(looper);

                                @Override
                                public void quit() {
                                    looper.quit();
                                }

                                @Override
                                public void post(Runnable runnable) {
                                    handler.post(runnable);
                                }

                            };
                        }

                    }
            );
        }
        return serverConnection;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);
    }

}
