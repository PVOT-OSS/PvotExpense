// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.Checkpoint
import org.prauga.expense.core.model.Rate
import org.prauga.expense.core.model.Rule
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction

@Dao
interface LedgerDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY archived, sort, name")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY kind, sort, name")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY occurredAt DESC")
    fun transactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: String): Transaction?

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND amountMinor = :amount AND occurredAt BETWEEN :from AND :to"
    )
    suspend fun candidates(amount: Long, from: Long, to: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND reference = :reference LIMIT 1")
    suspend fun byReference(reference: String): Transaction?

    @Query(
        "SELECT * FROM transactions WHERE deleted = 0 AND status = 'PENDING' ORDER BY occurredAt DESC"
    )
    suspend fun pendingNow(): List<Transaction>

    @Query("SELECT * FROM budgets WHERE deleted = 0") fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM schedules WHERE deleted = 0 ORDER BY nextDue")
    fun schedules(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE deleted = 0 AND active = 1 AND nextDue <= :day")
    suspend fun dueSchedules(day: Long): List<Schedule>

    @Query("SELECT * FROM rules WHERE deleted = 0") fun rules(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE deleted = 0") suspend fun rulesNow(): List<Rule>

    @Query("SELECT * FROM accounts WHERE deleted = 0") suspend fun accountsNow(): List<Account>

    @Query("SELECT * FROM categories WHERE deleted = 0") suspend fun categoriesNow(): List<Category>

    @Query("SELECT * FROM checkpoints WHERE deleted = 0 ORDER BY at DESC")
    fun checkpoints(): Flow<List<Checkpoint>>

    @Query("SELECT * FROM rates") fun rates(): Flow<List<Rate>>

    @Upsert suspend fun upsertAccounts(vararg items: Account)

    @Upsert suspend fun upsertCategories(vararg items: Category)

    @Upsert suspend fun upsertTransactions(vararg items: Transaction)

    @Upsert suspend fun upsertBudgets(vararg items: Budget)

    @Upsert suspend fun upsertSchedules(vararg items: Schedule)

    @Upsert suspend fun upsertRules(vararg items: Rule)

    @Upsert suspend fun upsertCheckpoints(vararg items: Checkpoint)

    @Upsert suspend fun upsertRates(vararg items: Rate)

    @Query("DELETE FROM rates WHERE currency = :currency") suspend fun deleteRate(currency: String)

    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<Account>

    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>

    @Query("SELECT * FROM transactions") suspend fun allTransactions(): List<Transaction>

    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<Budget>

    @Query("SELECT * FROM schedules") suspend fun allSchedules(): List<Schedule>

    @Query("SELECT * FROM rules") suspend fun allRules(): List<Rule>

    @Query("SELECT * FROM checkpoints") suspend fun allCheckpoints(): List<Checkpoint>

    @Query("SELECT * FROM rates") suspend fun allRates(): List<Rate>

    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Query("DELETE FROM transactions") suspend fun clearTransactions()

    @Query("DELETE FROM budgets") suspend fun clearBudgets()

    @Query("DELETE FROM schedules") suspend fun clearSchedules()

    @Query("DELETE FROM rules") suspend fun clearRules()

    @Query("DELETE FROM checkpoints") suspend fun clearCheckpoints()

    @Query("DELETE FROM rates") suspend fun clearRates()

    @RoomTransaction
    suspend fun snapshot(): Snapshot =
        Snapshot(
            accounts = allAccounts(),
            categories = allCategories(),
            transactions = allTransactions(),
            budgets = allBudgets(),
            schedules = allSchedules(),
            rules = allRules(),
            checkpoints = allCheckpoints(),
            rates = allRates(),
        )

    @RoomTransaction
    suspend fun replaceAll(s: Snapshot) {
        clearAccounts()
        clearCategories()
        clearTransactions()
        clearBudgets()
        clearSchedules()
        clearRules()
        clearCheckpoints()
        clearRates()
        upsertAccounts(*s.accounts.toTypedArray())
        upsertCategories(*s.categories.toTypedArray())
        upsertTransactions(*s.transactions.toTypedArray())
        upsertBudgets(*s.budgets.toTypedArray())
        upsertSchedules(*s.schedules.toTypedArray())
        upsertRules(*s.rules.toTypedArray())
        upsertCheckpoints(*s.checkpoints.toTypedArray())
        upsertRates(*s.rates.toTypedArray())
    }
}
