// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import java.time.LocalDate
import java.time.ZoneId
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType

/** Converts minor units of a currency into minor units of the base currency. */
fun interface Converter {
    fun toBase(minor: Long, currency: String): Long
}

fun List<Transaction>.confirmedExpenses(): List<Transaction> = filter {
    !it.deleted && it.status == TxStatus.CONFIRMED && it.type == TxType.EXPENSE
}

fun List<Transaction>.within(period: Period, zone: ZoneId): List<Transaction> {
    val from = period.startMillis(zone)
    val to = period.endMillis(zone)
    return filter { it.occurredAt in from until to }
}

/** Expense per category id (null = uncategorised), in base minor units. */
fun spendByCategory(transactions: List<Transaction>, fx: Converter): Map<String?, Long> =
    transactions
        .confirmedExpenses()
        .groupBy { it.categoryId }
        .mapValues { (_, txs) -> txs.sumOf { fx.toBase(it.amountMinor, it.currency) } }

data class BudgetStatus(val budget: Budget, val limitMinor: Long, val spentMinor: Long) {
    val leftMinor: Long
        get() = limitMinor - spentMinor

    val fraction: Float
        get() = if (limitMinor <= 0) 1f else spentMinor.toFloat() / limitMinor
}

/**
 * Each budget for [period], with last period's leftover (or overspend) carried when rolling over.
 */
fun budgetStatus(
    budgets: List<Budget>,
    transactions: List<Transaction>,
    period: Period,
    startDay: Int,
    zone: ZoneId,
    fx: Converter,
): List<BudgetStatus> {
    val spent = spendByCategory(transactions.within(period, zone), fx)
    val previous = spendByCategory(transactions.within(period.previous(startDay), zone), fx)
    return budgets
        .filter { !it.deleted }
        .map { b ->
            val carry = if (b.rollover) b.limitMinor - (previous[b.categoryId] ?: 0) else 0
            BudgetStatus(b, b.limitMinor + carry, spent[b.categoryId] ?: 0)
        }
}

data class Upcoming(val schedule: Schedule, val date: LocalDate)

fun upcoming(schedules: List<Schedule>, from: LocalDate, until: LocalDate): List<Upcoming> =
    schedules
        .flatMap { s ->
            occurrences(s, until).filter { !it.isBefore(from) }.map { Upcoming(s, it) }
        }
        .sortedBy { it.date }

data class SafeToSpend(
    val perDayMinor: Long,
    val spendableMinor: Long,
    val billsMinor: Long,
    val budgetLeftMinor: Long?,
    val daysLeft: Int,
    val spentMinor: Long,
    val forecastMinor: Long,
)

/**
 * What can still be spent per day until the period ends: money in cash, bank and wallets, minus
 * bills due before the period ends, capped by what's left in the budgets when there are any.
 * Expected income isn't counted, so the figure errs on the careful side.
 */
fun safeToSpend(
    accounts: List<Account>,
    transactions: List<Transaction>,
    schedules: List<Schedule>,
    budgets: List<Budget>,
    today: LocalDate,
    startDay: Int,
    zone: ZoneId,
    fx: Converter,
): SafeToSpend {
    val period = Period.containing(today, startDay)
    val live = accounts.filter { !it.deleted && !it.archived }
    val balance = balances(live, transactions)
    val spendable =
        live.filter { it.spendable }.sumOf { fx.toBase(balance[it.id] ?: 0, it.currency) }
    val bills =
        upcoming(schedules, today, period.endExclusive)
            .filter { it.schedule.type == TxType.EXPENSE }
            .sumOf { fx.toBase(it.schedule.amountMinor, it.schedule.currency) }
    val statuses = budgetStatus(budgets, transactions, period, startDay, zone, fx)
    val budgetLeft =
        if (statuses.isEmpty()) null else statuses.sumOf { it.leftMinor.coerceAtLeast(0) }
    val daysLeft = period.daysLeft(today)
    val free = (spendable - bills).coerceAtLeast(0)
    val pool = budgetLeft?.let { minOf(it, free) } ?: free
    val spent = spendByCategory(transactions.within(period, zone), fx).values.sum()
    val elapsed = (period.days - daysLeft + 1).coerceAtLeast(1)
    val forecast = spent * period.days / elapsed
    return SafeToSpend(pool / daysLeft, spendable, bills, budgetLeft, daysLeft, spent, forecast)
}

/** Assets minus liabilities, in base minor units. */
fun netWorth(accounts: List<Account>, transactions: List<Transaction>, fx: Converter): Long {
    val live = accounts.filter { !it.deleted }
    val balance = balances(live, transactions)
    return live.sumOf { fx.toBase(balance[it.id] ?: 0, it.currency) }
}
