// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import org.prauga.expense.core.model.Rule

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
}
