// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.tx

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.Instant
import org.prauga.expense.R
import org.prauga.expense.core.model.CategoryKind
import org.prauga.expense.core.model.Money
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxSource
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.DateDialog
import org.prauga.expense.ui.components.PickerField
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.components.SubScreen
import org.prauga.expense.ui.components.icon
import org.prauga.expense.ui.components.label

private enum class Picker {
    ACCOUNT,
    TO_ACCOUNT,
    CATEGORY,
}

@Composable
fun TransactionScreen(
    id: String?,
    draft: Transaction?,
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val existing = remember(id) { state.data.transactions.firstOrNull { it.id == id } }
    val start = existing ?: draft
    val accounts =
        state.data.accounts.filter {
            !it.archived || it.id == existing?.accountId || it.id == existing?.toAccountId
        }
    val fallback = accounts.firstOrNull { it.spendable } ?: accounts.firstOrNull()
    var type by rememberSaveable { mutableStateOf(start?.type ?: TxType.EXPENSE) }
    var accountId by rememberSaveable { mutableStateOf(start?.accountId ?: fallback?.id) }
    var toAccountId by rememberSaveable { mutableStateOf(start?.toAccountId) }
    val account = state.data.account(accountId)
    val toAccount = state.data.account(toAccountId)
    var amount by rememberSaveable {
        mutableStateOf(start?.let { Formats.plain(it.amountMinor, it.currency) }.orEmpty())
    }
    var toAmount by rememberSaveable {
        mutableStateOf(
            start
                ?.toAmountMinor
                ?.let { m -> Formats.plain(m, toAccount?.currency ?: "INR") }
                .orEmpty()
        )
    }
    var categoryId by rememberSaveable { mutableStateOf(start?.categoryId) }
    var payee by rememberSaveable { mutableStateOf(start?.payee.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(start?.note.orEmpty()) }
    val originalDate =
        start?.let { Instant.ofEpochMilli(it.occurredAt).atZone(state.zone).toLocalDate() }
    var date by rememberSaveable { mutableStateOf(originalDate ?: state.today) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val crossCurrency =
        type == TxType.TRANSFER &&
            toAccount != null &&
            account != null &&
            toAccount.currency != account.currency
    val title =
        stringResource(
            when {
                existing == null -> R.string.new_transaction
                existing.status == TxStatus.PENDING -> R.string.review_transaction
                else -> R.string.edit_transaction
            }
        )

    fun save() {
        val acc = account ?: return
        val minor =
            Money.parse(amount, acc.currency)?.minor?.takeIf { it > 0 }
                ?: run {
                    error = R.string.amount_invalid
                    return
                }
        if (type == TxType.TRANSFER && (toAccount == null || toAccount.id == acc.id)) {
            error = R.string.transfer_same
            return
        }
        val toMinor =
            if (type == TxType.TRANSFER)
                (if (crossCurrency) Money.parse(toAmount, toAccount!!.currency)?.minor ?: minor
                else minor)
            else null
        val time =
            when {
                start != null && date == originalDate -> start.occurredAt
                date == state.today -> System.currentTimeMillis()
                else -> date.atTime(12, 0).atZone(state.zone).toInstant().toEpochMilli()
            }
        val base =
            existing
                ?: Transaction(
                    type = type,
                    occurredAt = time,
                    accountId = acc.id,
                    amountMinor = minor,
                    currency = acc.currency,
                    source = TxSource.MANUAL,
                )
        vm.save(
            base.copy(
                type = type,
                status = TxStatus.CONFIRMED,
                occurredAt = time,
                accountId = acc.id,
                amountMinor = minor,
                currency = acc.currency,
                categoryId = if (type == TxType.TRANSFER) null else categoryId,
                toAccountId = if (type == TxType.TRANSFER) toAccount?.id else null,
                toAmountMinor = toMinor,
                payee = payee.trim(),
                note = note.trim(),
            )
        )
        onBack()
    }

    SubScreen(
        title = title,
        onBack = onBack,
        actions = {
            if (existing != null) {
                PvotTopBarAction(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.delete),
                    onClick = {
                        vm.delete(existing)
                        onBack()
                    },
                )
            }
        },
    ) {
        item {
            PvotSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.expense),
                        stringResource(R.string.income),
                        stringResource(R.string.transfer),
                    ),
                selectedIndex = type.ordinal,
                onSelect = {
                    type = TxType.entries[it]
                    if (
                        state.data.category(categoryId)?.kind !=
                            (if (type == TxType.INCOME) CategoryKind.INCOME
                            else CategoryKind.EXPENSE)
                    )
                        categoryId = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotTextField(
                value = amount,
                onValueChange = {
                    amount = it
                    error = null
                },
                label =
                    stringResource(R.string.amount) + (account?.let { " (${it.currency})" } ?: ""),
                singleLine = true,
                isError = error == R.string.amount_invalid,
                supportingText = error?.let { stringResource(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotListGroup {
                item {
                    PickerField(
                        stringResource(
                            if (type == TxType.TRANSFER) R.string.from_account else R.string.account
                        ),
                        account?.name ?: stringResource(R.string.none),
                        account?.type?.icon(),
                    ) {
                        picker = Picker.ACCOUNT
                    }
                }
                if (type == TxType.TRANSFER) {
                    item {
                        PickerField(
                            stringResource(R.string.to_account),
                            toAccount?.name ?: stringResource(R.string.none),
                            toAccount?.type?.icon(),
                        ) {
                            picker = Picker.TO_ACCOUNT
                        }
                    }
                } else {
                    item {
                        PickerField(
                            stringResource(R.string.category),
                            state.data.category(categoryId)?.name
                                ?: stringResource(R.string.no_category),
                            R.drawable.ic_category,
                        ) {
                            picker = Picker.CATEGORY
                        }
                    }
                }
                item {
                    PickerField(
                        stringResource(R.string.date),
                        Formats.date(date),
                        R.drawable.ic_calendar,
                    ) {
                        pickingDate = true
                    }
                }
            }
        }
        if (crossCurrency) {
            item {
                PvotTextField(
                    value = toAmount,
                    onValueChange = { toAmount = it },
                    label = stringResource(R.string.amount_received) + " (${toAccount!!.currency})",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            PvotTextField(
                value = payee,
                onValueChange = { payee = it },
                label = stringResource(R.string.payee),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotTextField(
                value = note,
                onValueChange = { note = it },
                label = stringResource(R.string.note),
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error == R.string.transfer_same)
            item { Caption(stringResource(R.string.transfer_same)) }
        item {
            PvotButton(
                text =
                    stringResource(
                        if (existing?.status == TxStatus.PENDING) R.string.save_confirm
                        else R.string.save
                    ),
                onClick = ::save,
                modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.S),
            )
        }
        existing?.raw?.let { raw ->
            item {
                PvotCard(Modifier.fillMaxWidth()) {
                    val from =
                        stringResource(
                            when (existing.source) {
                                TxSource.SMS -> R.string.source_sms
                                TxSource.NOTIFICATION -> R.string.source_notification
                                TxSource.RECURRING -> R.string.source_recurring
                                else -> R.string.source_share
                            }
                        )
                    Caption(
                        stringResource(R.string.captured_from, from),
                        Modifier.padding(
                            start = PvotSpacing.L,
                            top = PvotSpacing.L,
                            end = PvotSpacing.L,
                        ),
                    )
                    Text(
                        raw,
                        Modifier.padding(PvotSpacing.L),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    val accountChoices =
        accounts.map {
            Choice(
                it.id,
                it.name,
                "${stringResource(it.type.label())} · ${it.currency}",
                it.type.icon(),
            )
        }
    PickerSheet(
        picker == Picker.ACCOUNT,
        stringResource(R.string.account),
        accountChoices,
        onPick = { accountId = it },
        onDismiss = { picker = null },
    )
    PickerSheet(
        picker == Picker.TO_ACCOUNT,
        stringResource(R.string.to_account),
        accountChoices,
        onPick = {
            toAccountId = it
            error = null
        },
        onDismiss = { picker = null },
    )
    PickerSheet(
        picker == Picker.CATEGORY,
        stringResource(R.string.category),
        state.data.categories.filter { it.kind == kind }.map { Choice<String?>(it.id, it.name) } +
            Choice<String?>(null, stringResource(R.string.no_category)),
        onPick = { categoryId = it },
        onDismiss = { picker = null },
    )
    DateDialog(pickingDate, date, onPick = { date = it }, onDismiss = { pickingDate = false })
}
