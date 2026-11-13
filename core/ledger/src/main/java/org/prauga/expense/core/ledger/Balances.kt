// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType

/** Signed effect of [tx] on [accountId], in that account's minor units. */
fun Transaction.effectOn(accountId: String): Long =
    when {
        deleted || status != TxStatus.CONFIRMED -> 0
        type == TxType.TRANSFER && toAccountId == accountId && this.accountId == accountId -> 0
        type == TxType.TRANSFER && toAccountId == accountId -> toAmountMinor ?: amountMinor
        this.accountId != accountId -> 0
        type == TxType.INCOME -> amountMinor
        else -> -amountMinor
    }

/** Balances in each account's own currency, optionally only counting transactions up to [until]. */
fun balances(
    accounts: List<Account>,
    transactions: List<Transaction>,
    until: Long = Long.MAX_VALUE,
): Map<String, Long> {
    val result = accounts.associate { it.id to it.openingMinor }.toMutableMap()
    for (tx in transactions) {
        if (tx.occurredAt > until) continue
        result.computeIfPresent(tx.accountId) { id, v -> v + tx.effectOn(id) }
        tx.toAccountId?.let { to ->
            if (to != tx.accountId) result.computeIfPresent(to) { id, v -> v + tx.effectOn(id) }
        }
    }
    return result
}

/**
 * How far the app's balance is from what the bank reported at [checkpointAt]: positive means the
 * bank has more than the app knows about (a missing income, or an expense logged twice).
 */
fun drift(
    account: Account,
    transactions: List<Transaction>,
    checkpointAt: Long,
    reportedMinor: Long,
): Long = reportedMinor - (balances(listOf(account), transactions, checkpointAt)[account.id] ?: 0)
