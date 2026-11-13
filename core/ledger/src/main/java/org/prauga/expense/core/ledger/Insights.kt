// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import java.time.ZoneId
import org.prauga.expense.core.model.Transaction

data class CategoryInsight(val categoryId: String?, val spentMinor: Long, val averageMinor: Long) {
    /** Spending well above the usual: half as much again as the average, and not a trivial sum. */
    val unusual: Boolean
        get() =
            averageMinor > 0 &&
                spentMinor > averageMinor * 3 / 2 &&
                spentMinor - averageMinor > 50_00
}

/** This period's spend per category against the average of the [history] periods before it. */
fun categoryInsights(
    transactions: List<Transaction>,
    period: Period,
    startDay: Int,
    zone: ZoneId,
    fx: Converter,
    history: Int = 3,
): List<CategoryInsight> {
    val now = spendByCategory(transactions.within(period, zone), fx)
    val past = mutableMapOf<String?, Long>()
    var p = period
    repeat(history) {
        p = p.previous(startDay)
        spendByCategory(transactions.within(p, zone), fx).forEach { (k, v) ->
            past[k] = (past[k] ?: 0) + v
        }
    }
    return (now.keys + past.keys)
        .map { CategoryInsight(it, now[it] ?: 0, (past[it] ?: 0) / history) }
        .filter { it.spentMinor > 0 || it.averageMinor > 0 }
        .sortedByDescending { it.spentMinor }
}

fun topPayees(
    transactions: List<Transaction>,
    fx: Converter,
    limit: Int = 5,
): List<Pair<String, Long>> =
    transactions
        .confirmedExpenses()
        .filter { it.payee.isNotBlank() }
        .groupBy { it.payee.trim().lowercase() }
        .map { (_, txs) ->
            txs.first().payee.trim() to txs.sumOf { fx.toBase(it.amountMinor, it.currency) }
        }
        .sortedByDescending { it.second }
        .take(limit)

/** Spend per period for the last [count] periods, oldest first. */
fun monthlyTotals(
    transactions: List<Transaction>,
    current: Period,
    startDay: Int,
    zone: ZoneId,
    fx: Converter,
    count: Int = 6,
): List<Pair<Period, Long>> {
    val periods =
        generateSequence(current) { it.previous(startDay) }.take(count).toList().reversed()
    return periods.map { p -> p to spendByCategory(transactions.within(p, zone), fx).values.sum() }
}
