// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui

import android.icu.text.NumberFormat
import android.icu.util.Currency
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import org.prauga.expense.R
import org.prauga.expense.core.model.Money

val LocalHideAmounts = staticCompositionLocalOf { false }

object Formats {
    /** Rupees group in lakhs and crores even on an English (US) phone. */
    private fun localeFor(currency: String): Locale {
        val default = Locale.getDefault()
        return if (currency == "INR" && default.country != "IN")
            Locale.Builder().setLanguage(default.language).setRegion("IN").build()
        else default
    }

    fun money(minor: Long, currency: String, signed: Boolean = false): String {
        val format = NumberFormat.getCurrencyInstance(localeFor(currency))
        format.currency = Currency.getInstance(currency)
        val exponent = Money.exponentOf(currency)
        val whole = exponent == 0 || abs(minor) % BigDecimal.TEN.pow(exponent).toLong() == 0L
        format.minimumFractionDigits = if (whole) 0 else exponent
        format.maximumFractionDigits = exponent
        val text = format.format(BigDecimal.valueOf(abs(minor), exponent))
        return when {
            minor < 0 -> "−$text"
            signed && minor > 0 -> "+$text"
            else -> text
        }
    }

    /** The amount as typed into a field: "1234.5", no symbols or grouping. */
    fun plain(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, Money.exponentOf(currency)).stripTrailingZeros().toPlainString()

    fun date(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year)
            date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        else date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    fun time(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis)
            .atZone(zone)
            .toLocalTime()
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    fun relative(millis: Long): String = DateUtils.getRelativeTimeSpanString(millis).toString()

    fun currencyName(code: String): String =
        runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }
            .getOrDefault(code)
}

@Composable
fun money(minor: Long, currency: String, signed: Boolean = false): String =
    if (LocalHideAmounts.current) stringResource(R.string.hidden_amount)
    else Formats.money(minor, currency, signed)

@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String =
    when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> Formats.shortDate(date, today)
    }
