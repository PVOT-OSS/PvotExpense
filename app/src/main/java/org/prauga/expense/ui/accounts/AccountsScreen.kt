// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.Instant
import org.prauga.expense.R
import org.prauga.expense.core.ledger.drift
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.AccountType
import org.prauga.expense.core.model.Money
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.Nav
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.Heading
import org.prauga.expense.ui.components.IconTile
import org.prauga.expense.ui.components.SubScreen
import org.prauga.expense.ui.components.TxRow
import org.prauga.expense.ui.components.icon
import org.prauga.expense.ui.components.label
import org.prauga.expense.ui.money

@Composable
fun AccountsScreen(state: AppState, nav: Nav) {
    SubScreen(
        title = stringResource(R.string.accounts),
        onBack = nav::back,
        actions = {
            PvotTopBarAction(
                painterResource(R.drawable.ic_plus),
                stringResource(R.string.add_account),
                onClick = { nav.editAccount(null) },
            )
        },
    ) {
        item {
            PvotCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(PvotSpacing.L)) {
                    Text(
                        stringResource(R.string.net_worth),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        money(state.netWorth, state.base),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
        }
        AccountType.entries.forEach { type ->
            val accounts = state.data.accounts.filter { it.type == type }
            if (accounts.isEmpty()) return@forEach
            item("h$type") { Heading(stringResource(type.label())) }
            item("g$type") {
                PvotListGroup {
                    accounts.forEach { a ->
                        item {
                            PvotListRow(
                                title = a.name,
                                supportingText =
                                    if (a.archived) stringResource(R.string.archived)
                                    else a.last4?.let { "•••• $it" },
                                leading = { IconTile(a.type.icon()) },
                                trailing = {
                                    Text(
                                        money(state.balances[a.id] ?: a.openingMinor, a.currency),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                },
                                onClick = { nav.account(a.id) },
                            )
                        }
                    }
                }
            }
        }
        item {
            PvotButton(
                stringResource(R.string.add_account),
                onClick = { nav.editAccount(null) },
                style = PvotButtonStyle.Tonal,
            )
        }
    }
}

@Composable
fun AccountScreen(id: String, state: AppState, vm: AppViewModel, nav: Nav) {
    val account = state.data.account(id) ?: return
    val balance = state.balances[id] ?: account.openingMinor
    var reconciling by remember { mutableStateOf(false) }
    val checkpoint = state.data.checkpoints.firstOrNull { it.accountId == id }
    val txs = state.data.confirmed.filter { it.accountId == id || it.toAccountId == id }
    SubScreen(
        title = account.name,
        onBack = nav::back,
        actions = {
            PvotTopBarAction(
                painterResource(R.drawable.ic_edit),
                stringResource(R.string.edit),
                onClick = { nav.editAccount(id) },
            )
        },
    ) {
        item {
            PvotCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(PvotSpacing.L),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
                ) {
                    Text(
                        stringResource(R.string.balance),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        money(balance, account.currency),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    when (account.type) {
                        AccountType.PERSON ->
                            Caption(
                                when {
                                    balance > 0 ->
                                        stringResource(
                                            R.string.owes_you,
                                            money(balance, account.currency),
                                        )
                                    balance < 0 ->
                                        stringResource(
                                            R.string.you_owe,
                                            money(-balance, account.currency),
                                        )
                                    else -> stringResource(R.string.settled)
                                },
                                Modifier,
                            )
                        AccountType.CARD -> {
                            account.dueDay?.let {
                                Caption(stringResource(R.string.card_due, it), Modifier)
                            }
                            account.limitMinor?.let {
                                Caption(
                                    stringResource(
                                        R.string.available_credit,
                                        money(it + balance, account.currency),
                                    ),
                                    Modifier,
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
        item("rec-h") { Heading(stringResource(R.string.reconcile)) }
        item("rec") {
            PvotListGroup {
                item {
                    val d =
                        checkpoint?.let {
                            drift(account, state.data.confirmed, it.at, it.balanceMinor)
                        }
                    PvotListRow(
                        title =
                            when {
                                checkpoint == null -> stringResource(R.string.reconcile_summary)
                                d == 0L -> stringResource(R.string.matches)
                                else ->
                                    stringResource(
                                        R.string.drift,
                                        money(d ?: 0, account.currency, signed = true),
                                    )
                            },
                        supportingText =
                            checkpoint?.let {
                                stringResource(
                                    R.string.last_checkpoint,
                                    money(it.balanceMinor, account.currency),
                                    Formats.shortDate(
                                        Instant.ofEpochMilli(it.at)
                                            .atZone(state.zone)
                                            .toLocalDate(),
                                        state.today,
                                    ),
                                )
                            },
                        leading = { IconTile(R.drawable.ic_scale) },
                        onClick = { reconciling = true },
                    )
                }
            }
        }
        if (txs.isNotEmpty()) {
            item("tx-h") { Heading(stringResource(R.string.recent)) }
            item("tx") {
                PvotListGroup {
                    txs.take(50).forEach { tx ->
                        item {
                            TxRow(
                                tx,
                                state.data,
                                state.zone,
                                onClick = { nav.transaction(tx.id) },
                                showDate = true,
                            )
                        }
                    }
                }
            }
        }
    }
    ReconcileDialog(reconciling, account, balance, vm) { reconciling = false }
}

@Composable
private fun ReconcileDialog(
    visible: Boolean,
    account: Account,
    balance: Long,
    vm: AppViewModel,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var adjust by rememberSaveable { mutableStateOf(true) }
    val label = stringResource(R.string.adjustment)
    PvotDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.bank_says),
        message = stringResource(R.string.reconcile_summary),
        content = {
            PvotTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = Formats.plain(balance, account.currency),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            PvotSwitchRow(
                title = stringResource(R.string.adjust),
                checked = adjust,
                onCheckedChange = { adjust = it },
            )
        },
    ) {
        val reported = Money.parse(text.replace("−", "-"), account.currency)?.minor
        PvotDialogAction(
            stringResource(R.string.save),
            onClick = {
                reported?.let { vm.reconcile(account, it, adjust, label) }
                text = ""
                onDismiss()
            },
            style = PvotDialogActionStyle.Primary,
            enabled = reported != null,
        )
        PvotDialogAction(stringResource(R.string.cancel), onClick = onDismiss)
    }
}
