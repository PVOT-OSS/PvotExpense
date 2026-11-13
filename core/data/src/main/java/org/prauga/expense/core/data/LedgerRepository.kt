// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.prauga.expense.core.capture.CapturedPayment
import org.prauga.expense.core.capture.Payees
import org.prauga.expense.core.capture.Rules
import org.prauga.expense.core.ledger.Converter
import org.prauga.expense.core.ledger.advance
import org.prauga.expense.core.ledger.merge
import org.prauga.expense.core.ledger.sameMoneyMovement
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.AccountType
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.CategoryKind
import org.prauga.expense.core.model.Checkpoint
import org.prauga.expense.core.model.Money
import org.prauga.expense.core.model.Rate
import org.prauga.expense.core.model.Rule
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxSource
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType

/** Everything the screens show, in one consistent read. */
data class LedgerData(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val checkpoints: List<Checkpoint> = emptyList(),
    val rates: List<Rate> = emptyList(),
    val rules: List<Rule> = emptyList(),
) {
    val confirmed: List<Transaction>
        get() = transactions.filter { it.status == TxStatus.CONFIRMED }

    val pending: List<Transaction>
        get() = transactions.filter { it.status == TxStatus.PENDING }

    fun account(id: String?): Account? = accounts.firstOrNull { it.id == id }

    fun category(id: String?): Category? = categories.firstOrNull { it.id == id }

    fun converter(base: String): Converter {
        val table =
            rates.associate { it.currency to (it.perBase.toBigDecimalOrNull() ?: BigDecimal.ONE) }
        return Converter { minor, currency ->
            if (currency == base) minor
            else {
                val rate = table[currency] ?: BigDecimal.ONE
                Money.of(Money(minor, currency).decimal.multiply(rate, MathContext.DECIMAL64), base)
                    .minor
            }
        }
    }

    /** Currencies used by accounts that have no exchange rate to [base] yet. */
    fun missingRates(base: String): List<String> =
        accounts
            .map { it.currency }
            .distinct()
            .filter { c -> c != base && rates.none { it.currency == c } }
}

class LedgerRepository(
    private val dao: LedgerDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val data: Flow<LedgerData> =
        combine(
                dao.accounts(),
                dao.categories(),
                dao.transactions(),
                dao.budgets(),
                dao.schedules(),
            ) { a, c, t, b, s ->
                LedgerData(a, c, t, b, s)
            }
            .combine(dao.checkpoints()) { d, cp -> d.copy(checkpoints = cp) }
            .combine(dao.rates()) { d, r -> d.copy(rates = r) }
            .combine(dao.rules()) { d, r -> d.copy(rules = r) }

    suspend fun transaction(id: String): Transaction? = dao.transaction(id)

    suspend fun save(account: Account) = dao.upsertAccounts(account.copy(updatedAt = clock()))

    suspend fun save(category: Category) = dao.upsertCategories(category.copy(updatedAt = clock()))

    suspend fun save(budget: Budget) = dao.upsertBudgets(budget.copy(updatedAt = clock()))

    suspend fun save(schedule: Schedule) = dao.upsertSchedules(schedule.copy(updatedAt = clock()))

    suspend fun save(checkpoint: Checkpoint) =
        dao.upsertCheckpoints(checkpoint.copy(updatedAt = clock()))

    suspend fun saveRate(currency: String, perBase: String) =
        dao.upsertRates(Rate(currency, perBase, clock()))

    suspend fun deleteRate(currency: String) = dao.deleteRate(currency)

    suspend fun delete(account: Account) = save(account.copy(deleted = true))

    suspend fun delete(category: Category) = save(category.copy(deleted = true))

    suspend fun delete(budget: Budget) = save(budget.copy(deleted = true))

    suspend fun delete(schedule: Schedule) = save(schedule.copy(deleted = true))

    suspend fun delete(tx: Transaction) =
        dao.upsertTransactions(tx.copy(deleted = true, updatedAt = clock()))

    suspend fun restore(tx: Transaction) =
        dao.upsertTransactions(tx.copy(deleted = false, updatedAt = clock()))

    /** Saves a confirmed transaction and learns its payee's category for next time. */
    suspend fun save(tx: Transaction) {
        val saved = tx.copy(updatedAt = clock())
        dao.upsertTransactions(saved)
        if (saved.status == TxStatus.CONFIRMED && saved.type != TxType.TRANSFER) learn(saved)
    }

    suspend fun confirm(tx: Transaction) = save(tx.copy(status = TxStatus.CONFIRMED))

    suspend fun confirmAll() = dao.pendingNow().forEach { confirm(it) }

    private suspend fun learn(tx: Transaction) {
        Rules.learn(tx.payee, tx.categoryId, tx.accountId, dao.rulesNow(), clock())?.let {
            dao.upsertRules(it)
        }
    }

    /**
     * Files a payment read from an SMS or notification into the inbox. The same payment arriving
     * from a second source is merged into the first instead of being added twice.
     */
    suspend fun capture(
        payment: CapturedPayment,
        at: Long,
        source: TxSource,
        raw: String,
    ): Transaction? {
        val reference = payment.reference
        if (reference != null && dao.byReference(reference)?.let { it.source == source } == true)
            return null
        val accounts = dao.accountsNow()
        val rules = dao.rulesNow()
        val rule = Rules.match(payment.payee, rules)
        val account =
            payment.accountHint?.let { hint ->
                accounts.firstOrNull { a ->
                    a.last4?.let { hint.endsWith(it) || it.endsWith(hint) } == true
                }
            }
                ?: rule?.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
                ?: defaultAccount(accounts)
        val draft =
            Transaction(
                type = payment.type,
                status = TxStatus.PENDING,
                source = source,
                occurredAt = at,
                accountId = account.id,
                amountMinor = payment.amountMinor,
                currency = payment.currency,
                categoryId = rule?.categoryId ?: fallbackCategory(payment.type),
                payee = payment.payee,
                reference = payment.reference,
                accountHint = payment.accountHint,
                raw = raw,
                updatedAt = clock(),
            )
        payment.balanceMinor?.let { balance ->
            if (payment.accountHint != null && account.last4 != null) {
                dao.upsertCheckpoints(
                    Checkpoint(
                        accountId = account.id,
                        at = at,
                        balanceMinor = balance,
                        source = source,
                        updatedAt = clock(),
                    )
                )
            }
        }
        val twin =
            dao.candidates(payment.amountMinor, at - DAY, at + DAY).firstOrNull {
                it.id != draft.id && sameMoneyMovement(it, draft)
            }
        if (twin != null) {
            dao.upsertTransactions(merge(twin, draft).copy(updatedAt = clock()))
            return null
        }
        dao.upsertTransactions(draft)
        return draft
    }

    private suspend fun fallbackCategory(type: TxType): String? {
        if (type == TxType.TRANSFER) return null
        val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        return dao.categoriesNow()
            .filter { it.kind == kind }
            .firstOrNull { it.name.equals(OTHER, ignoreCase = true) }
            ?.id
    }

    private suspend fun defaultAccount(accounts: List<Account>): Account =
        accounts.firstOrNull { it.type == AccountType.BANK && !it.archived }
            ?: accounts.firstOrNull { it.spendable && !it.archived }
            ?: accounts.firstOrNull()
            ?: Account(name = "Cash", type = AccountType.CASH, currency = "INR").also { save(it) }

    /** Posts every schedule due on or before [today]; auto-confirmed ones skip the inbox. */
    suspend fun postDue(today: LocalDate, zone: ZoneId): Int {
        var posted = 0
        for (s in dao.dueSchedules(today.toEpochDay())) {
            var due = LocalDate.ofEpochDay(s.nextDue)
            while (!due.isAfter(today)) {
                val tx =
                    Transaction(
                        type = s.type,
                        status = if (s.autoConfirm) TxStatus.CONFIRMED else TxStatus.PENDING,
                        source = TxSource.RECURRING,
                        occurredAt = due.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
                        accountId = s.accountId,
                        amountMinor = s.amountMinor,
                        currency = s.currency,
                        categoryId = s.categoryId,
                        toAccountId = s.toAccountId,
                        toAmountMinor = if (s.toAccountId != null) s.amountMinor else null,
                        payee = s.payee,
                        note = s.note,
                        scheduleId = s.id,
                        updatedAt = clock(),
                    )
                dao.upsertTransactions(tx)
                posted++
                due = s.advance(due)
            }
            dao.upsertSchedules(s.copy(nextDue = due.toEpochDay(), updatedAt = clock()))
        }
        return posted
    }

    /** First run: a cash account in [currency] and the usual categories. */
    suspend fun seedIfEmpty(currency: String, names: SeedNames) {
        if (dao.allCategories().isEmpty()) {
            val expense =
                names.expense.mapIndexed { i, n ->
                    Category(
                        name = n,
                        kind = CategoryKind.EXPENSE,
                        color = i,
                        sort = i,
                        updatedAt = clock(),
                    )
                }
            val income =
                names.income.mapIndexed { i, n ->
                    Category(
                        name = n,
                        kind = CategoryKind.INCOME,
                        color = i + 3,
                        sort = i,
                        updatedAt = clock(),
                    )
                }
            dao.upsertCategories(*(expense + income).toTypedArray())
        }
        if (dao.allAccounts().isEmpty()) {
            dao.upsertAccounts(
                Account(
                    name = names.cash,
                    type = AccountType.CASH,
                    currency = currency,
                    updatedAt = clock(),
                )
            )
        }
    }

    suspend fun snapshot(): Snapshot = dao.snapshot()

    suspend fun replaceAll(snapshot: Snapshot) = dao.replaceAll(snapshot)

    /**
     * Adds CSV rows as confirmed transactions, creating missing accounts and categories by name.
     */
    suspend fun import(rows: List<ImportRow>, base: String, zone: ZoneId): Int {
        val accounts = dao.accountsNow().toMutableList()
        val categories = dao.categoriesNow().toMutableList()
        val txs =
            rows.map { r ->
                val currency = r.currency?.uppercase(Locale.ROOT) ?: base
                val account =
                    r.account?.let { name ->
                        accounts.firstOrNull { it.name.equals(name, true) }
                            ?: Account(
                                    name = name,
                                    type = AccountType.BANK,
                                    currency = currency,
                                    updatedAt = clock(),
                                )
                                .also {
                                    accounts += it
                                    dao.upsertAccounts(it)
                                }
                    } ?: defaultAccount(accounts)
                val type = r.type ?: if (r.amount.signum() < 0) TxType.EXPENSE else TxType.INCOME
                val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val category =
                    r.category
                        ?.takeIf { type != TxType.TRANSFER }
                        ?.let { name ->
                            categories.firstOrNull { it.name.equals(name, true) && it.kind == kind }
                                ?: Category(
                                        name = name,
                                        kind = kind,
                                        color = categories.size,
                                        updatedAt = clock(),
                                    )
                                    .also {
                                        categories += it
                                        dao.upsertCategories(it)
                                    }
                        }
                Transaction(
                    type = type,
                    source = TxSource.IMPORT,
                    occurredAt = r.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                    accountId = account.id,
                    amountMinor = Money.of(r.amount.abs(), account.currency).minor,
                    currency = account.currency,
                    categoryId = category?.id,
                    payee = Payees.display(r.payee),
                    note = r.note,
                    updatedAt = clock(),
                )
            }
        dao.upsertTransactions(*txs.toTypedArray())
        return txs.size
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val OTHER = "Other"
    }
}

data class SeedNames(val cash: String, val expense: List<String>, val income: List<String>)
