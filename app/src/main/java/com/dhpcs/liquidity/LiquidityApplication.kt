package com.dhpcs.liquidity

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import com.dhpcs.liquidity.provider.LiquidityContract
import net.danlew.android.joda.JodaTimeAndroid
import org.joda.time.*

class LiquidityApplication : Application() {

    companion object {

        private var gameDatabase: BoardGame.Companion.GameDatabase? = null

        fun getGameDatabase(context: Context): BoardGame.Companion.GameDatabase {
            if (gameDatabase == null) {
                gameDatabase = object : BoardGame.Companion.GameDatabase {

                    override fun insertGame(zoneId: String,
                                            created: Long,
                                            expires: Long,
                                            name: String?
                    ): Long {
                        val contentValues = ContentValues()
                        contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId)
                        contentValues.put(LiquidityContract.Games.CREATED, created)
                        contentValues.put(LiquidityContract.Games.EXPIRES, expires)
                        contentValues.put(LiquidityContract.Games.NAME, name)
                        return ContentUris.parseId(
                                context.contentResolver.insert(
                                        LiquidityContract.Games.CONTENT_URI,
                                        contentValues
                                )
                        )
                    }

                    override fun checkAndUpdateGame(zoneId: String, name: String?): Long? {
                        val existingEntry = context.contentResolver.query(
                                LiquidityContract.Games.CONTENT_URI,
                                arrayOf(LiquidityContract.Games.ID, LiquidityContract.Games.NAME),
                                "${LiquidityContract.Games.ZONE_ID} = ?",
                                arrayOf(zoneId), null
                        )
                        return existingEntry?.use {
                            if (!existingEntry.moveToFirst()) {
                                null
                            } else {
                                val gameId = existingEntry.getLong(
                                        existingEntry.getColumnIndexOrThrow(
                                                LiquidityContract.Games.ID
                                        )
                                )
                                if (existingEntry.getString(
                                                existingEntry.getColumnIndexOrThrow(
                                                        LiquidityContract.Games.NAME
                                                )
                                        ) != name) {
                                    updateGameName(gameId, name)
                                }
                                gameId
                            }
                        }
                    }

                    override fun updateGameName(gameId: Long, name: String?) {
                        val contentValues = ContentValues()
                        contentValues.put(LiquidityContract.Games.NAME, name)
                        context.contentResolver.update(
                                ContentUris.withAppendedId(
                                        LiquidityContract.Games.CONTENT_URI,
                                        gameId
                                ),
                                contentValues,
                                null,
                                null
                        )
                    }

                }
            }
            return gameDatabase!!
        }

        private var serverConnection: ServerConnection? = null

        fun getServerConnection(context: Context): ServerConnection {
            if (serverConnection == null) {
                serverConnection = ServerConnection(context.filesDir)
            }
            return serverConnection!!
        }

        fun getRelativeTimeSpanString(context: Context,
                                      time: ReadableInstant,
                                      now: ReadableInstant,
                                      minResolution: Long): String {
            val flags = (android.text.format.DateUtils.FORMAT_SHOW_DATE or
                    android.text.format.DateUtils.FORMAT_SHOW_YEAR or
                    android.text.format.DateUtils.FORMAT_ABBREV_MONTH)
            return getRelativeTimeSpanString(context, time, now, minResolution, flags)
        }

        @SuppressLint("PrivateResource")
        private fun getRelativeTimeSpanString(context: Context,
                                              time: ReadableInstant,
                                              now: ReadableInstant,
                                              minResolution: Long,
                                              flags: Int): String {
            val abbrevRelative = flags and (android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE or
                    android.text.format.DateUtils.FORMAT_ABBREV_ALL) != 0

            val timeRounded = DateTime(time).withMillisOfSecond(0)
            val nowRounded = DateTime(now).withMillisOfSecond(0)
            val past = !nowRounded.isBefore(timeRounded)
            val interval = if (past) {
                Interval(timeRounded, nowRounded)
            } else {
                Interval(nowRounded, timeRounded)
            }

            val count: Long
            val resId: Int
            if (minResolution < android.text.format.DateUtils.MINUTE_IN_MILLIS &&
                    Minutes.minutesIn(interval).isLessThan(Minutes.ONE)) {
                count = Seconds.secondsIn(interval).seconds.toLong()
                resId = if (past) {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_num_seconds_ago
                    } else {
                        R.plurals.joda_time_android_num_seconds_ago
                    }
                } else {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_in_num_seconds
                    } else {
                        R.plurals.joda_time_android_in_num_seconds
                    }
                }
            } else if (minResolution < android.text.format.DateUtils.HOUR_IN_MILLIS &&
                    Hours.hoursIn(interval).isLessThan(Hours.ONE)) {
                count = Minutes.minutesIn(interval).minutes.toLong()
                resId = if (past) {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_num_minutes_ago
                    } else {
                        R.plurals.joda_time_android_num_minutes_ago
                    }
                } else {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_in_num_minutes
                    } else {
                        R.plurals.joda_time_android_in_num_minutes
                    }
                }
            } else if (minResolution < android.text.format.DateUtils.DAY_IN_MILLIS &&
                    Days.daysIn(interval).isLessThan(Days.ONE)) {
                count = Hours.hoursIn(interval).hours.toLong()
                resId = if (past) {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_num_hours_ago
                    } else {
                        R.plurals.joda_time_android_num_hours_ago
                    }
                } else {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_in_num_hours
                    } else {
                        R.plurals.joda_time_android_in_num_hours
                    }
                }
            } else if (minResolution < android.text.format.DateUtils.WEEK_IN_MILLIS &&
                    (if (past) {
                        Weeks.weeksBetween(timeRounded.toLocalDate(), nowRounded.toLocalDate())
                    } else {
                        Weeks.weeksBetween(nowRounded.toLocalDate(), timeRounded.toLocalDate())
                    }).isLessThan(Weeks.ONE)) {
                count = (if (past) {
                    Days.daysBetween(timeRounded.toLocalDate(), nowRounded.toLocalDate())
                } else {
                    Days.daysBetween(nowRounded.toLocalDate(), timeRounded.toLocalDate())
                }.days).toLong()
                resId = if (past) {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_num_days_ago
                    } else {
                        R.plurals.joda_time_android_num_days_ago
                    }
                } else {
                    if (abbrevRelative) {
                        R.plurals.joda_time_android_abbrev_in_num_days
                    } else {
                        R.plurals.joda_time_android_in_num_days
                    }
                }
            } else {
                return net.danlew.android.joda.DateUtils.formatDateRange(context, time, time, flags)
            }

            val format = context.resources.getQuantityString(resId, count.toInt())
            return String.format(format, count)
        }

    }

    override fun onCreate() {
        super.onCreate()
        JodaTimeAndroid.init(this)
    }

}
