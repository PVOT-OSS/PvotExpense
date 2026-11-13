// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.plan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.LocalDate
import org.prauga.expense.R
import org.prauga.expense.core.model.CategoryKind
import org.prauga.expense.core.model.Frequency
import org.prauga.expense.core.model.Money
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.DateDialog
import org.prauga.expense.ui.components.PickerField
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.components.SubScreen
import org.prauga.expense.ui.components.icon

@Composable
fun ScheduleScreen(id: String?, state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    val existing = remember(id) { state.data.schedules.firstOrNull { it.id == id } }
    val fallback =
        state.data.accounts.firstOrNull { it.spendable } ?: state.data.accounts.firstOrNull()
    var type by rememberSaveable { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: fallback?.id) }
    var toAccountId by rememberSaveable { mutableStateOf(existing?.toAccountId) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var amount by rememberSaveable {
        mutableStateOf(existing?.let { Formats.plain(it.amountMinor, it.currency) }.orEmpty())
    }
    var payee by rememberSaveable { mutableStateOf(existing?.payee.orEmpty()) }
    var frequency by rememberSaveable { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
    var nextDue by rememberSaveable {
        mutableStateOf(
            existing?.let { LocalDate.ofEpochDay(it.nextDue) } ?: state.today.plusDays(1)
        )
    }
    var remind by rememberSaveable { mutableStateOf((existing?.remindDays ?: 2).toString()) }
    var auto by rememberSaveable { mutableStateOf(existing?.autoConfirm ?: false) }
    var picking by remember { mutableStateOf(0) }
    var pickingDate by remember { mutableStateOf(false) }
    val account = state.data.account(accountId)
    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val minor = account?.let { Money.parse(amount, it.currency)?.minor }?.takeIf { it > 0 }

    SubScreen(
        title =
            stringResource(if (existing == null) R.string.add_schedule else R.string.edit_schedule),
        onBack = onBack,
        actions = {
            if (existing != null)
                PvotTopBarAction(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.delete),
                    onClick = {
                        vm.delete(existing)
                        onBack()
                    },
                )
        },
    ) {
        item {
            PvotSegmentedControl(
                listOf(
                    stringResource(R.string.expense),
                    stringResource(R.string.income),
                    stringResource(R.string.transfer),
                ),
                type.ordinal,
                {
                    type = TxType.entries[it]
                    categoryId = null
                },
                Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotTextField(
                payee,
                { payee = it },
                label = stringResource(R.string.payee),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotTextField(
                amount,
                { amount = it },
                label = stringResource(R.string.amount),
                singleLine = true,
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
                        picking = 1
                    }
                }
                if (type == TxType.TRANSFER)
                    item {
                        PickerField(
                            stringResource(R.string.to_account),
                            state.data.account(toAccountId)?.name ?: stringResource(R.string.none),
                        ) {
                            picking = 2
                        }
                    }
                else
                    item {
                        PickerField(
                            stringResource(R.string.category),
                            state.data.category(categoryId)?.name
                                ?: stringResource(R.string.no_category),
                            R.drawable.ic_category,
                        ) {
                            picking = 3
                        }
                    }
                item {
                    PickerField(
                        stringResource(R.string.next_due),
                        Formats.date(nextDue),
                        R.drawable.ic_calendar,
                    ) {
                        pickingDate = true
                    }
                }
            }
        }
        item {
            PvotSegmentedControl(
                listOf(
                    stringResource(R.string.weekly),
                    stringResource(R.string.monthly),
                    stringResource(R.string.yearly),
                ),
                frequency.ordinal,
                { frequency = Frequency.entries[it] },
                Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotTextField(
                remind,
                { remind = it.filter(Char::isDigit).take(2) },
                label = stringResource(R.string.remind_days),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotListGroup {
                item {
                    PvotSwitchRow(
                        title = stringResource(R.string.auto_confirm),
                        summary = stringResource(R.string.auto_confirm_summary),
                        checked = auto,
                        onCheckedChange = { auto = it },
                    )
                }
            }
        }
        item {
            PvotButton(
                stringResource(R.string.save),
                enabled =
                    minor != null &&
                        payee.isNotBlank() &&
                        account != null &&
                        (type != TxType.TRANSFER ||
                            (toAccountId != null && toAccountId != accountId)),
                onClick = {
                    val acc = account ?: return@PvotButton
                    val base =
                        existing
                            ?: Schedule(
                                type = type,
                                accountId = acc.id,
                                amountMinor = minor!!,
                                currency = acc.currency,
                                payee = payee,
                                nextDue = nextDue.toEpochDay(),
                            )
                    vm.save(
                        base.copy(
                            type = type,
                            accountId = acc.id,
                            amountMinor = minor!!,
                            currency = acc.currency,
                            categoryId = if (type == TxType.TRANSFER) null else categoryId,
                            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                            payee = payee.trim(),
                            frequency = frequency,
                            nextDue = nextDue.toEpochDay(),
                            remindDays = remind.toIntOrNull() ?: 0,
                            autoConfirm = auto,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.S),
            )
        }
    }
    val accountChoices =
        state.data.accounts
            .filter { !it.archived }
            .map { Choice(it.id, it.name, it.currency, it.type.icon()) }
    PickerSheet(
        picking == 1,
        stringResource(R.string.account),
        accountChoices,
        onPick = { accountId = it },
        onDismiss = { picking = 0 },
    )
    PickerSheet(
        picking == 2,
        stringResource(R.string.to_account),
        accountChoices,
        onPick = { toAccountId = it },
        onDismiss = { picking = 0 },
    )
    PickerSheet(
        picking == 3,
        stringResource(R.string.category),
        state.data.categories.filter { it.kind == kind }.map { Choice<String?>(it.id, it.name) },
        onPick = { categoryId = it },
        onDismiss = { picking = 0 },
    )
    DateDialog(pickingDate, nextDue, onPick = { nextDue = it }, onDismiss = { pickingDate = false })
}
