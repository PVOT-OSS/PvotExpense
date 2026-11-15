// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import org.prauga.expense.core.model.Rule
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxType

object Rules {
    /** The best rule for [payee]: an exact key match, else the longest pattern contained in it. */
    fun match(payee: String, rules: List<Rule>): Rule? {
        val key = Payees.key(payee)
        if (key.isEmpty()) return null
        val live = rules.filter { !it.deleted && it.pattern.isNotEmpty() }
        return live.firstOrNull { it.pattern == key }
            ?: live
                .filter { key.contains(it.pattern) }
                .maxWithOrNull(compareBy({ it.pattern.length }, { it.hits }))
    }

    /** The rule to save after the user filed [payee] under [categoryId]. */
    fun learn(
        payee: String,
        categoryId: String?,
        accountId: String?,
        rules: List<Rule>,
        now: Long,
    ): Rule? {
        val key = Payees.key(payee)
        if (key.length < 2 || categoryId == null) return null
        val existing = rules.firstOrNull { it.pattern == key && !it.deleted }
        return existing?.copy(
            categoryId = categoryId,
            accountId = accountId ?: existing.accountId,
            hits = existing.hits + 1,
            updatedAt = now,
        ) ?: Rule(pattern = key, categoryId = categoryId, accountId = accountId, updatedAt = now)
    }

    /** The most recent choice per payee wins. */
    fun learnAll(transactions: List<Transaction>, rules: List<Rule>, now: Long): List<Rule> {
        val byPattern = rules.filter { !it.deleted }.associateBy { it.pattern }.toMutableMap()
        val changed = mutableSetOf<String>()
        for (tx in transactions.filter { it.type != TxType.TRANSFER }.sortedBy { it.occurredAt }) {
            val rule =
                learn(tx.payee, tx.categoryId, tx.accountId, byPattern.values.toList(), now)
                    ?: continue
            byPattern[rule.pattern] = rule
            changed += rule.pattern
        }
        return changed.mapNotNull { byPattern[it] }
    }
}
