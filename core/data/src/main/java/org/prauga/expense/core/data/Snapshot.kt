// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.Checkpoint
import org.prauga.expense.core.model.Rate
import org.prauga.expense.core.model.Rule
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction

/** Everything the app stores, as written to a backup. */
@Serializable
data class Snapshot(
    val format: Int = 1,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val checkpoints: List<Checkpoint> = emptyList(),
    val rates: List<Rate> = emptyList(),
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun fromJson(text: String): Snapshot = JSON.decodeFromString(serializer(), text)
    }
}
