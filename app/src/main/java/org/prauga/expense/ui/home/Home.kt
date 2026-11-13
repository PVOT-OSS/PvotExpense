// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgress
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.LocalDate
import org.prauga.expense.R
import org.prauga.expense.core.ledger.Upcoming
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.Nav
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.CardCaption
import org.prauga.expense.ui.components.Heading
import org.prauga.expense.ui.components.IconTile
import org.prauga.expense.ui.components.TxRow
import org.prauga.expense.ui.components.icon
import org.prauga.expense.ui.money

fun LazyListScope.homeContent(vm: AppViewModel, state: AppState, nav: Nav, onSeeAll: () -> Unit) {
    item("safe") { SafeCard(state) }
    val missing = state.data.missingRates(state.base)
    if (missing.isNotEmpty()) {
        item("rates") {
            PvotCard(onClick = nav::rates) {
                Text(
                    stringResource(R.string.missing_rates, missing.joinToString()),
                    Modifier.padding(PvotSpacing.L),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    val pending = state.data.pending
    if (pending.isNotEmpty()) {
        item("inbox-h") { Heading(stringResource(R.string.inbox_count, pending.size)) }
        item("inbox") {
            PvotListGroup {
                pending.take(3).forEach { tx ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                TxRow(
                                    tx,
                                    state.data,
                                    state.zone,
                                    onClick = { nav.transaction(tx.id) },
                                )
                            }
                            PvotIconButton(
                                painterResource(R.drawable.ic_check),
                                stringResource(R.string.confirm),
                                onClick = { vm.confirm(tx) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        item("inbox-a") {
            Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
                PvotButton(
                    stringResource(R.string.inbox_review),
                    onClick = nav::inbox,
                    style = PvotButtonStyle.Tonal,
                )
                PvotButton(
                    stringResource(R.string.confirm_all),
                    onClick = vm::confirmAll,
                    style = PvotButtonStyle.Text,
                )
            }
        }
    }
    val soon = state.upcoming.filter { it.date.isBefore(state.today.plusDays(15)) }.take(4)
    if (soon.isNotEmpty()) {
        item("up-h") { Heading(stringResource(R.string.upcoming)) }
        item("up") {
            PvotListGroup {
                soon.forEach { u ->
                    item { UpcomingRow(u, state.today) { nav.schedule(u.schedule.id) } }
                }
            }
        }
    }
    item("acc-h") { Heading(stringResource(R.string.accounts)) }
    item("acc") {
        PvotListGroup {
            item {
                PvotListRow(
                    title = stringResource(R.string.net_worth),
                    trailing = {
                        Text(
                            money(state.netWorth, state.base),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    onClick = nav::accounts,
                )
            }
            state.data.accounts
                .filter { !it.archived }
                .take(4)
                .forEach { a ->
                    item {
                        PvotListRow(
                            title = a.name,
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
    val recent = state.data.transactions.filter { it.status == TxStatus.CONFIRMED }.take(5)
    item("recent-h") { Heading(stringResource(R.string.recent)) }
    if (recent.isEmpty()) {
        item("empty") { Caption(stringResource(R.string.empty_home)) }
    } else {
        item("recent") {
            PvotListGroup {
                recent.forEach { tx ->
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
        item("all") {
            PvotButton(
                stringResource(R.string.see_all),
                onClick = onSeeAll,
                style = PvotButtonStyle.Text,
            )
        }
    }
}

@Composable
private fun SafeCard(state: AppState) {
    val safe = state.safe ?: return
    PvotCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(PvotSpacing.L),
            verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
        ) {
            Text(
                stringResource(R.string.safe_to_spend),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                money(safe.perDayMinor, state.base),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                pluralStringResource(R.plurals.safe_to_spend_detail, safe.daysLeft, safe.daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val limit =
                safe.budgetLeftMinor?.let { it + safe.spentMinor }
                    ?: (safe.spendableMinor + safe.spentMinor)
            PvotLinearProgress(
                progress = {
                    if (limit <= 0) 0f else (safe.spentMinor.toFloat() / limit).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = PvotSpacing.S),
            )
            Stat(stringResource(R.string.spent_this_month), money(safe.spentMinor, state.base))
            Stat(stringResource(R.string.safe_spendable), money(safe.spendableMinor, state.base))
            if (safe.billsMinor > 0)
                Stat(stringResource(R.string.safe_bills), money(-safe.billsMinor, state.base))
            safe.budgetLeftMinor?.let {
                Stat(stringResource(R.string.safe_budget_left), money(it, state.base))
            }
            if (safe.spentMinor > 0)
                CardCaption(
                    stringResource(R.string.forecast, money(safe.forecastMinor, state.base)),
                    Modifier.padding(top = PvotSpacing.XS),
                )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UpcomingRow(u: Upcoming, today: LocalDate, onClick: () -> Unit) {
    PvotListRow(
        title = u.schedule.payee,
        supportingText = stringResource(R.string.due_on, Formats.shortDate(u.date, today)),
        leading = { IconTile(R.drawable.ic_repeat) },
        trailing = {
            Text(
                money(u.schedule.amountMinor, u.schedule.currency),
                style = MaterialTheme.typography.titleSmall,
            )
        },
        onClick = onClick,
    )
}
