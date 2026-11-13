// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import java.time.LocalDate
import org.prauga.expense.core.model.Frequency
import org.prauga.expense.core.model.Schedule

fun Schedule.advance(from: LocalDate): LocalDate {
    val n = interval.coerceAtLeast(1).toLong()
    return when (frequency) {
        Frequency.WEEKLY -> from.plusWeeks(n)
        Frequency.MONTHLY -> from.plusMonths(n)
        Frequency.YEARLY -> from.plusYears(n)
    }
}

/** Due dates of [schedule] from its next due date up to (excluding) [until]. */
fun occurrences(schedule: Schedule, until: LocalDate, limit: Int = 400): List<LocalDate> {
    if (!schedule.active || schedule.deleted) return emptyList()
    val out = mutableListOf<LocalDate>()
    var d = LocalDate.ofEpochDay(schedule.nextDue)
    while (d.isBefore(until) && out.size < limit) {
        out += d
        d = schedule.advance(d)
    }
    return out
}
