package com.dhpcs.liquidity

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import net.danlew.android.joda.JodaTimeAndroid
import org.joda.time.*
import java.math.BigDecimal
import java.text.Collator
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

class LiquidityApplication : Application() {

    companion object {

        @SuppressLint("PrivateResource")
        fun getRelativeTimeSpanString(context: Context,
                                      time: ReadableInstant,
                                      now: ReadableInstant,
                                      minResolution: Long): String {
            val timeRounded = DateTime(time).withMillisOfSecond(0)
            val nowRounded = DateTime(now).withMillisOfSecond(0)
            val past = !nowRounded.isBefore(timeRounded)
            val interval = if (past) {
                Interval(timeRounded, nowRounded)
            } else {
                Interval(nowRounded, timeRounded)
            }

            val count: Int
            val resId: Int
            if (minResolution < android.text.format.DateUtils.MINUTE_IN_MILLIS &&
                    Minutes.minutesIn(interval).isLessThan(Minutes.ONE)) {
                count = Seconds.secondsIn(interval).seconds
                resId = if (past) {
                    R.plurals.joda_time_android_num_seconds_ago
                } else {
                    R.plurals.joda_time_android_in_num_seconds
                }
            } else if (minResolution < android.text.format.DateUtils.HOUR_IN_MILLIS &&
                    Hours.hoursIn(interval).isLessThan(Hours.ONE)) {
                count = Minutes.minutesIn(interval).minutes
                resId = if (past) {
                    R.plurals.joda_time_android_num_minutes_ago
                } else {
                    R.plurals.joda_time_android_in_num_minutes
                }
            } else if (minResolution < android.text.format.DateUtils.DAY_IN_MILLIS &&
                    Days.daysIn(interval).isLessThan(Days.ONE)) {
                count = Hours.hoursIn(interval).hours
                resId = if (past) {
                    R.plurals.joda_time_android_num_hours_ago
                } else {
                    R.plurals.joda_time_android_in_num_hours
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
                }.days)
                resId = if (past) {
                    R.plurals.joda_time_android_num_days_ago
                } else {
                    R.plurals.joda_time_android_in_num_days
                }
            } else {
                return net.danlew.android.joda.DateUtils.formatDateRange(
                        context,
                        time,
                        time,
                        android.text.format.DateUtils.FORMAT_SHOW_DATE or
                                android.text.format.DateUtils.FORMAT_SHOW_YEAR or
                                android.text.format.DateUtils.FORMAT_ABBREV_MONTH
                )
            }

            return String.format(context.resources.getQuantityString(resId, count), count)
        }

        fun formatCurrency(context: Context, currencyCode: String?): String {
            return if (currencyCode == null) {
                ""
            } else {
                try {
                    val currency = Currency.getInstance(currencyCode)
                    if (currency.symbol == currency.currencyCode) {
                        context.getString(
                                R.string.currency_code_format_string,
                                currency.currencyCode
                        )
                    } else {
                        currency.symbol
                    }
                } catch (_: IllegalArgumentException) {
                    context.getString(
                            R.string.currency_code_format_string,
                            currencyCode
                    )
                }
            }
        }

        fun formatCurrencyValue(context: Context, currencyCode: String?, value: BigDecimal): String {
            val scaleAmount: Int
            val scaledValue: BigDecimal
            val multiplier: String
            when {
                value >= BigDecimal(1000000000000000) -> {
                    scaleAmount = -15
                    scaledValue = value.scaleByPowerOfTen(-15)
                    multiplier = context.getString(R.string.value_multiplier_quadrillion)
                }
                value >= BigDecimal(1000000000000) -> {
                    scaleAmount = -12
                    scaledValue = value.scaleByPowerOfTen(-12)
                    multiplier = context.getString(R.string.value_multiplier_trillion)
                }
                value >= BigDecimal(1000000000) -> {
                    scaleAmount = -9
                    scaledValue = value.scaleByPowerOfTen(-9)
                    multiplier = context.getString(R.string.value_multiplier_billion)
                }
                value >= BigDecimal(1000000) -> {
                    scaleAmount = -6
                    scaledValue = value.scaleByPowerOfTen(-6)
                    multiplier = context.getString(R.string.value_multiplier_million)
                }
                value >= BigDecimal(1000) -> {
                    scaleAmount = -3
                    scaledValue = value.scaleByPowerOfTen(-3)
                    multiplier = context.getString(R.string.value_multiplier_thousand)
                }
                else -> {
                    scaleAmount = 0
                    scaledValue = value
                    multiplier = ""
                }
            }

            val maximumFractionDigits: Int
            val minimumFractionDigits: Int
            when (scaleAmount) {
                0 -> when {
                    scaledValue.scale() == 0 -> {
                        maximumFractionDigits = 0
                        minimumFractionDigits = 0
                    }
                    else -> {
                        maximumFractionDigits = scaledValue.scale()
                        minimumFractionDigits = 2
                    }
                }
                else -> {
                    maximumFractionDigits = scaledValue.scale()
                    minimumFractionDigits = 0
                }
            }

            val numberFormat = NumberFormat.getNumberInstance() as DecimalFormat
            numberFormat.maximumFractionDigits = maximumFractionDigits
            numberFormat.minimumFractionDigits = minimumFractionDigits

            return context.getString(
                    R.string.currency_value_format_string,
                    formatCurrency(context, currencyCode),
                    numberFormat.format(scaledValue),
                    multiplier
            )
        }

        fun formatNullable(context: Context, nullable: String?): String {
            return nullable ?: context.getString(R.string.unnamed)
        }

        fun identityComparator(context: Context): Comparator<BoardGame.Companion.Identity> {
            return object : Comparator<BoardGame.Companion.Identity> {

                private val collator = Collator.getInstance()

                override fun compare(lhs: BoardGame.Companion.Identity,
                                     rhs: BoardGame.Companion.Identity
                ): Int {
                    return when (val nameOrdered = collator.compare(
                            formatNullable(context, lhs.name),
                            formatNullable(context, rhs.name)
                    )) {
                        0 -> lhs.memberId.compareTo(rhs.memberId)
                        else -> nameOrdered
                    }
                }

            }
        }

        fun playerComparator(context: Context): Comparator<BoardGame.Companion.Player> {
            return object : Comparator<BoardGame.Companion.Player> {

                private val collator = Collator.getInstance()

                override fun compare(lhs: BoardGame.Companion.Player,
                                     rhs: BoardGame.Companion.Player
                ): Int {
                    return when (val nameOrdered = collator.compare(
                            formatNullable(context, lhs.name),
                            formatNullable(context, rhs.name)
                    )) {
                        0 -> lhs.memberId.compareTo(rhs.memberId)
                        else -> nameOrdered
                    }
                }

            }
        }

    }

    override fun onCreate() {
        super.onCreate()
        JodaTimeAndroid.init(this)
    }

}
