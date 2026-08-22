package com.replog.app.util

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private val locale: Locale = Locale.US

    fun oneDecimal(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else String.format("%.1f", r)
    }

    fun int(v: Double): String = v.roundToInt().toString()

    fun thousands(v: Int): String = String.format(locale, "%,d", v)

    fun kcal(v: Double): String = thousands(v.roundToInt())

    fun minutesToHM(minutes: Int): String =
        if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"

    fun dayName(epochDay: Long, short: Boolean = true): String =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(
            if (short) TextStyle.SHORT else TextStyle.FULL, locale
        )

    fun dateLabel(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "${d.month.getDisplayName(TextStyle.SHORT, locale)} ${d.dayOfMonth}"
    }

    fun fullDateLabel(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, " +
            "${d.month.getDisplayName(TextStyle.SHORT, locale)} ${d.dayOfMonth}"
    }
}
