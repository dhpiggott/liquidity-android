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
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDexApplication;

import com.dhpcs.liquidity.boardgame.BoardGame;
import com.dhpcs.liquidity.client.ServerConnection;
import com.dhpcs.liquidity.model.ZoneId;
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

import java.util.concurrent.Executor;

public class LiquidityApplication extends MultiDexApplication {

    private static BoardGame.GameDatabase gameDatabase;

    public static BoardGame.GameDatabase getGameDatabase(final Context context) {
        if (gameDatabase == null) {
            gameDatabase = new BoardGame.GameDatabase() {

                @Override
                public Long checkAndUpdateGame(ZoneId zoneId, String name) {
                    Cursor existingEntry = context.getContentResolver().query(
                            LiquidityContract.Games.INSTANCE.getCONTENT_URI(),
                            new String[]{
                                    LiquidityContract.Games.ID,
                                    LiquidityContract.Games.NAME
                            },
                            LiquidityContract.Games.ZONE_ID + " = ?",
                            new String[]{zoneId.id()},
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
                                                LiquidityContract.Games.ID
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
                    contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id());
                    contentValues.put(LiquidityContract.Games.CREATED, created);
                    contentValues.put(LiquidityContract.Games.EXPIRES, expires);
                    contentValues.put(LiquidityContract.Games.NAME, name);
                    return ContentUris.parseId(
                            context.getContentResolver().insert(
                                    LiquidityContract.Games.INSTANCE.getCONTENT_URI(),
                                    contentValues
                            )
                    );
                }

                @Override
                public void updateGameName(long gameId, String name) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(LiquidityContract.Games.NAME, name);
                    context.getContentResolver().update(
                            ContentUris.withAppendedId(LiquidityContract.Games.INSTANCE.getCONTENT_URI(), gameId),
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
    private static String getRelativeTimeSpanString(Context context,
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

    private static final Executor mainThreadExecutor = new Executor() {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable runnable) {
            handler.post(runnable);
        }
    };

    public static Executor getMainThreadExecutor() {
        return mainThreadExecutor;
    }

    private static ServerConnection serverConnection;

    public static ServerConnection getServerConnection(final Context context) {
        if (serverConnection == null) {
            PRNGFixes.apply();
            serverConnection = new ServerConnection(
                    context.getFilesDir(),
                    serverConnection -> new ServerConnection.ConnectivityStatePublisher() {

                        private final IntentFilter connectionStateFilter =
                                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
                        private final BroadcastReceiver connectionStateReceiver =
                                new BroadcastReceiver() {

                                    @Override
                                    public void onReceive(Context context1, Intent intent) {
                                        serverConnection.handleConnectivityStateChange();
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
                            context.unregisterReceiver(connectionStateReceiver);
                        }

                        private final ConnectivityManager connectivityManager =
                                ((ConnectivityManager)
                                        context.getSystemService(Context.CONNECTIVITY_SERVICE));

                        @Override
                        public boolean isConnectionAvailable() {
                            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                            return activeNetwork != null && activeNetwork.isConnected();
                        }

                    },
                    mainThreadExecutor
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
