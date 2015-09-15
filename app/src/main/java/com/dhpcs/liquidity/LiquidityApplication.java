package com.dhpcs.liquidity;

import android.content.Context;
import android.support.multidex.MultiDexApplication;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Interval;
import org.joda.time.Minutes;
import org.joda.time.ReadableInstant;
import org.joda.time.Seconds;
import org.joda.time.Weeks;

public class LiquidityApplication extends MultiDexApplication {

    public static String getRelativeTimeSpanString(Context context,
                                                   ReadableInstant time,
                                                   ReadableInstant now,
                                                   long minResolution) {
        int flags = android.text.format.DateUtils.FORMAT_SHOW_DATE
                | android.text.format.DateUtils.FORMAT_SHOW_YEAR |
                android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
        return getRelativeTimeSpanString(context, time, now, minResolution, flags);
    }

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

    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);
    }

}
