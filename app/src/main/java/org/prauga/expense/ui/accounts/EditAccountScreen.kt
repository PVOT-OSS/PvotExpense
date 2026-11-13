// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.accounts

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
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Currency
import java.util.Locale
import org.prauga.expense.R
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.AccountType
import org.prauga.expense.core.model.Money
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.PickerField
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.components.SubScreen
import org.prauga.expense.ui.components.icon
import org.prauga.expense.ui.components.label

@Composable
fun EditAccountScreen(
    id: String?,
    state: AppState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val existing = remember(id) { state.data.account(id) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: AccountType.BANK) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: state.base) }
    var opening by rememberSaveable {
        mutableStateOf(
            existing
                ?.let {
                    if (it.openingMinor != 0L) Formats.plain(it.openingMinor, it.currency) else ""
                }
                .orEmpty()
        )
    }
    var last4 by rememberSaveable { mutableStateOf(existing?.last4.orEmpty()) }
    var dueDay by rememberSaveable { mutableStateOf(existing?.dueDay?.toString().orEmpty()) }
    var statementDay by rememberSaveable {
        mutableStateOf(existing?.statementDay?.toString().orEmpty())
    }
    var limit by rememberSaveable {
        mutableStateOf(existing?.limitMinor?.let { Formats.plain(it, existing.currency) }.orEmpty())
    }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var pickingType by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val validCurrency =
        runCatching { Currency.getInstance(currency.uppercase(Locale.ROOT)) }.isSuccess

    SubScreen(
        title =
            stringResource(if (existing == null) R.string.add_account else R.string.edit_account),
        onBack = onBack,
        actions = {
            if (existing != null)
                PvotTopBarAction(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.delete),
                    onClick = { confirmDelete = true },
                )
        },
    ) {
        item {
            PvotTextField(
                name,
                { name = it },
                label = stringResource(R.string.account_name),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotListGroup {
                item {
                    PickerField(
                        stringResource(R.string.account_type),
                        stringResource(type.label()),
                        type.icon(),
                    ) {
                        pickingType = true
                    }
                }
            }
        }
        item {
            PvotTextField(
                currency,
                { currency = it.uppercase(Locale.ROOT).take(3) },
                label = stringResource(R.string.currency),
                supportingText = if (validCurrency) Formats.currencyName(currency) else null,
                isError = !validCurrency,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PvotTextField(
                opening,
                { opening = it },
                label = stringResource(R.string.opening_balance),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (type == AccountType.BANK || type == AccountType.CARD || type == AccountType.WALLET) {
            item {
                PvotTextField(
                    last4,
                    { last4 = it.filter(Char::isDigit).take(4) },
                    label = stringResource(R.string.last4),
                    supportingText = stringResource(R.string.last4_summary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (type == AccountType.CARD) {
            item {
                PvotTextField(
                    limit,
                    { limit = it },
                    label = stringResource(R.string.credit_limit),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PvotTextField(
                    statementDay,
                    { statementDay = it.filter(Char::isDigit).take(2) },
                    label = stringResource(R.string.statement_day),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PvotTextField(
                    dueDay,
                    { dueDay = it.filter(Char::isDigit).take(2) },
                    label = stringResource(R.string.due_day),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (existing != null)
            item {
                PvotListGroup {
                    item {
                        PvotSwitchRow(
                            title = stringResource(R.string.archived),
                            checked = archived,
                            onCheckedChange = { archived = it },
                        )
                    }
                }
            }
        item {
            PvotButton(
                stringResource(R.string.save),
                enabled = name.isNotBlank() && validCurrency,
                onClick = {
                    val code = currency.uppercase(Locale.ROOT)
                    val base = existing ?: Account(name = name, type = type, currency = code)
                    vm.save(
                        base.copy(
                            name = name.trim(),
                            type = type,
                            currency = code,
                            openingMinor = Money.parse(opening.replace("−", "-"), code)?.minor ?: 0,
                            last4 = last4.ifBlank { null },
                            dueDay = dueDay.toIntOrNull()?.coerceIn(1, 31),
                            statementDay = statementDay.toIntOrNull()?.coerceIn(1, 31),
                            limitMinor = Money.parse(limit, code)?.minor,
                            archived = archived,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.S),
            )
        }
    }
    PickerSheet(
        pickingType,
        stringResource(R.string.account_type),
        AccountType.entries.map { Choice(it, stringResource(it.label()), icon = it.icon()) },
        onPick = { type = it },
        onDismiss = { pickingType = false },
    )
    PvotDialog(
        visible = confirmDelete,
        onDismissRequest = { confirmDelete = false },
        title = stringResource(R.string.delete_account_title),
        message = stringResource(R.string.delete_account_message),
        destructive = true,
    ) {
        PvotDialogAction(
            stringResource(R.string.delete),
            onClick = {
                existing?.let(vm::delete)
                confirmDelete = false
                onDeleted()
            },
            style = PvotDialogActionStyle.Destructive,
        )
        PvotDialogAction(stringResource(R.string.cancel), onClick = { confirmDelete = false })
    }
}
