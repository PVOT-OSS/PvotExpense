// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A budgeting month that starts on [startDay] (1–28), e.g. the 25th for salary cycles. */
data class Period(val start: LocalDate, val endExclusive: LocalDate) {
    val days: Int
        get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillis(zone: ZoneId): Long = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    fun daysLeft(today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, endExclusive).toInt().coerceIn(1, days)

    fun previous(startDay: Int): Period = containing(start.minusDays(1), startDay)

    fun next(startDay: Int): Period = containing(endExclusive, startDay)

    companion object {
        fun containing(date: LocalDate, startDay: Int): Period {
            val day = startDay.coerceIn(1, 28)
            val start =
                if (date.dayOfMonth >= day) date.withDayOfMonth(day)
                else date.minusMonths(1).withDayOfMonth(day)
            return Period(start, start.plusMonths(1))
        }
    }
}

fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
