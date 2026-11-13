// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.prauga.expense.AppContainer
import org.prauga.expense.R
import org.prauga.expense.core.capture.QuickAddParser
import org.prauga.expense.core.capture.QuickEntry
import org.prauga.expense.core.capture.Rules
import org.prauga.expense.core.data.LedgerData
import org.prauga.expense.core.data.Settings
import org.prauga.expense.core.ledger.BudgetStatus
import org.prauga.expense.core.ledger.Converter
import org.prauga.expense.core.ledger.Period
import org.prauga.expense.core.ledger.SafeToSpend
import org.prauga.expense.core.ledger.Upcoming
import org.prauga.expense.core.ledger.balances
import org.prauga.expense.core.ledger.budgetStatus
import org.prauga.expense.core.ledger.netWorth
import org.prauga.expense.core.ledger.safeToSpend
import org.prauga.expense.core.ledger.upcoming
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.CategoryKind
import org.prauga.expense.core.model.Checkpoint
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxSource
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType

data class AppState(
    val loaded: Boolean = false,
    val data: LedgerData = LedgerData(),
    val settings: Settings = Settings(baseCurrency = "INR"),
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.now(),
    val balances: Map<String, Long> = emptyMap(),
    val safe: SafeToSpend? = null,
    val budgets: List<BudgetStatus> = emptyList(),
    val upcoming: List<Upcoming> = emptyList(),
    val netWorth: Long = 0,
    val period: Period = Period.containing(LocalDate.now(), 1),
) {
    val base: String
        get() = settings.baseCurrency

    val fx: Converter
        get() = data.converter(base)
}

/** A snackbar line; with [count] set, [text] is a plurals resource. */
data class Message(
    val text: Int,
    val args: List<Any> = emptyList(),
    val undo: (() -> Unit)? = null,
    val count: Int? = null,
)

class AppViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<AppState> =
        combine(c.repository.data, c.settings.settings) { data, settings -> derive(data, settings) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val messages: SharedFlow<Message> = _messages

    private fun derive(data: LedgerData, settings: Settings): AppState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val fx = data.converter(settings.baseCurrency)
        val start = settings.monthStartDay
        val confirmed = data.confirmed
        val period = Period.containing(today, start)
        return AppState(
            loaded = true,
            data = data,
            settings = settings,
            zone = zone,
            today = today,
            balances = balances(data.accounts, confirmed),
            safe =
                safeToSpend(
                    data.accounts,
                    confirmed,
                    data.schedules,
                    data.budgets,
                    today,
                    start,
                    zone,
                    fx,
                ),
            budgets = budgetStatus(data.budgets, confirmed, period, start, zone, fx),
            upcoming = upcoming(data.schedules, today, today.plusDays(30)),
            netWorth = netWorth(data.accounts, confirmed, fx),
            period = period,
        )
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun say(@StringRes text: Int, vararg args: Any) {
        _messages.tryEmit(Message(text, args.toList()))
    }

    fun save(tx: Transaction) = launch { c.repository.save(tx) }

    fun confirm(tx: Transaction) = launch { c.repository.confirm(tx) }

    fun confirmAll() = launch { c.repository.confirmAll() }

    fun delete(tx: Transaction) = launch {
        c.repository.delete(tx)
        _messages.emit(Message(R.string.deleted, undo = { launch { c.repository.restore(tx) } }))
    }

    fun save(account: Account) = launch { c.repository.save(account) }

    fun delete(account: Account) = launch { c.repository.delete(account) }

    fun save(category: Category) = launch { c.repository.save(category) }

    fun delete(category: Category) = launch { c.repository.delete(category) }

    fun save(budget: Budget) = launch { c.repository.save(budget) }

    fun delete(budget: Budget) = launch { c.repository.delete(budget) }

    fun save(schedule: Schedule) = launch {
        c.repository.save(schedule)
        c.repository.postDue(LocalDate.now(), ZoneId.systemDefault())
    }

    fun delete(schedule: Schedule) = launch { c.repository.delete(schedule) }

    fun saveRate(currency: String, perBase: String) = launch {
        c.repository.saveRate(currency, perBase)
    }

    fun deleteRate(currency: String) = launch { c.repository.deleteRate(currency) }

    /** Records the bank's figure, and optionally books the difference so the balances match. */
    fun reconcile(account: Account, reportedMinor: Long, adjust: Boolean, label: String) = launch {
        val now = System.currentTimeMillis()
        c.repository.save(
            Checkpoint(accountId = account.id, at = now, balanceMinor = reportedMinor)
        )
        if (!adjust) return@launch
        val current = state.value.balances[account.id] ?: account.openingMinor
        val diff = reportedMinor - current
        if (diff == 0L) return@launch
        c.repository.save(
            Transaction(
                type = if (diff > 0) TxType.INCOME else TxType.EXPENSE,
                occurredAt = now,
                accountId = account.id,
                amountMinor = abs(diff),
                currency = account.currency,
                payee = label,
            )
        )
    }

    fun parse(text: String): QuickEntry =
        QuickAddParser.parse(text, LocalDate.now(), state.value.base)

    /** Turns a quick-add line into a transaction, matching #category and @account by name. */
    fun resolve(entry: QuickEntry): Transaction? {
        val s = state.value
        val amount = entry.amountMinor ?: return null
        val accounts = s.data.accounts.filter { !it.archived }
        fun account(hint: String?): Account? =
            hint?.lowercase(Locale.ROOT)?.let { h ->
                accounts.firstOrNull { it.name.lowercase(Locale.ROOT) == h }
                    ?: accounts.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(h) }
                    ?: accounts.firstOrNull { it.last4 == h }
            }
        val kind = if (entry.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val rule = Rules.match(entry.payee, s.data.rules)
        val from =
            account(entry.accountHint)
                ?: rule?.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
                ?: accounts.firstOrNull { it.currency == entry.currency && it.spendable }
                ?: accounts.firstOrNull { it.spendable }
                ?: accounts.firstOrNull()
                ?: return null
        val category =
            entry.categoryHint
                ?.lowercase(Locale.ROOT)
                ?.let { h ->
                    s.data.categories
                        .filter { it.kind == kind }
                        .let { cats ->
                            cats.firstOrNull { it.name.lowercase(Locale.ROOT) == h }
                                ?: cats.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(h) }
                        }
                }
                ?.id ?: rule?.categoryId?.takeIf { id -> s.data.category(id)?.kind == kind }
        val to = if (entry.type == TxType.TRANSFER) account(entry.toAccountHint) else null
        val time =
            if (entry.date == LocalDate.now()) System.currentTimeMillis()
            else entry.date.atTime(12, 0).atZone(s.zone).toInstant().toEpochMilli()
        return Transaction(
            type = if (entry.type == TxType.TRANSFER && to == null) TxType.EXPENSE else entry.type,
            source = TxSource.QUICK_ADD,
            occurredAt = time,
            accountId = from.id,
            amountMinor = amount,
            currency = from.currency,
            categoryId = if (entry.type == TxType.TRANSFER) null else category,
            toAccountId = to?.id,
            toAmountMinor = to?.let { amount },
            payee = entry.payee,
        )
    }

    fun quickAdd(text: String): Boolean {
        val tx = resolve(parse(text)) ?: return false
        save(tx)
        _messages.tryEmit(
            Message(
                R.string.added,
                listOf(Formats.money(tx.amountMinor, tx.currency)),
                undo = { launch { c.repository.delete(tx) } },
            )
        )
        return true
    }

    fun pendingCount(): Int = state.value.data.transactions.count { it.status == TxStatus.PENDING }

    // Settings
    fun setBaseCurrency(code: String) = launch { c.settings.setBaseCurrency(code) }

    fun setMonthStartDay(day: Int) = launch { c.settings.setMonthStartDay(day) }

    fun setLock(on: Boolean) = launch { c.settings.setLock(on) }

    fun setHideAmounts(on: Boolean) = launch { c.settings.setHideAmounts(on) }

    fun setSecureScreen(on: Boolean) = launch { c.settings.setSecureScreen(on) }

    fun setCaptureNotifications(on: Boolean) = launch { c.settings.setCaptureNotifications(on) }

    fun setCaptureSms(on: Boolean) = launch { c.settings.setCaptureSms(on) }

    fun setCaptureMessages(on: Boolean) = launch {
        c.settings.setCaptureMessages(on)
        if (on) c.capture.pullFromMessages()
    }

    fun importSms() = launch {
        val n = c.capture.importSmsHistory()
        _messages.emit(Message(R.plurals.imported_sms, listOf(n), count = n))
    }

    fun setOnboarded(currency: String) = launch {
        c.settings.setBaseCurrency(currency)
        c.settings.setOnboarded()
    }

    fun setBackupFolder(uri: String) = launch { c.settings.setBackupUri(uri) }

    fun setPassphrase(passphrase: CharArray) = launch { c.settings.setPassphrase(passphrase) }

    fun backupNow() = launch {
        val result = c.backups.backUp()
        _messages.emit(
            result.fold(
                { Message(R.string.backup_done) },
                { Message(R.string.backup_failed, listOf(it.message.orEmpty())) },
            )
        )
    }

    fun restore(uri: String, passphrase: CharArray) = launch {
        _messages.emit(c.backups.restore(uri, passphrase))
    }

    fun exportCsv(uri: String) = launch { _messages.emit(c.backups.exportCsv(uri)) }

    fun importCsv(uri: String) = launch { _messages.emit(c.backups.importCsv(uri)) }

    fun shared(text: String) = launch { c.capture.fromShare(text) }
}
