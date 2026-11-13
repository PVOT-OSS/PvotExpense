// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.activity

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import java.time.Instant
import java.util.Locale
import org.prauga.expense.R
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.Nav
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.TxRow
import org.prauga.expense.ui.dayLabel
import org.prauga.expense.ui.money

data class ActivityFilter(val query: String = "", val type: Int = 0)

fun LazyListScope.activityContent(
    state: AppState,
    nav: Nav,
    filter: ActivityFilter,
    onFilter: (ActivityFilter) -> Unit,
) {
    item("search") {
        PvotTextField(
            value = filter.query,
            onValueChange = { onFilter(filter.copy(query = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.search_hint),
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null)
            },
            singleLine = true,
        )
    }
    item("type") {
        PvotSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.filter_all),
                    stringResource(R.string.expense),
                    stringResource(R.string.income),
                    stringResource(R.string.transfer),
                ),
            selectedIndex = filter.type,
            onSelect = { onFilter(filter.copy(type = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val q = filter.query.trim().lowercase(Locale.ROOT)
    val type = listOf(null, TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)[filter.type]
    val txs =
        state.data.transactions.filter { tx ->
            tx.status == TxStatus.CONFIRMED &&
                (type == null || tx.type == type) &&
                (q.isEmpty() ||
                    tx.payee.lowercase(Locale.ROOT).contains(q) ||
                    tx.note.lowercase(Locale.ROOT).contains(q) ||
                    state.data.category(tx.categoryId)?.name?.lowercase(Locale.ROOT)?.contains(q) ==
                        true ||
                    state.data.account(tx.accountId)?.name?.lowercase(Locale.ROOT)?.contains(q) ==
                        true)
        }
    if (txs.isEmpty()) {
        item("empty") { Caption(stringResource(R.string.empty_activity)) }
        return
    }
    txs.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(state.zone).toLocalDate() }
        .entries
        .take(120)
        .forEach { (day, items) ->
            item("d$day") {
                val spent =
                    items
                        .filter { it.type == TxType.EXPENSE }
                        .sumOf { state.fx.toBase(it.amountMinor, it.currency) }
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
                    Text(
                        dayLabel(day, state.today),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (spent > 0)
                        Text(
                            money(-spent, state.base),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
            item("g$day") {
                PvotListGroup {
                    items.forEach { tx ->
                        item {
                            TxRow(tx, state.data, state.zone, onClick = { nav.transaction(tx.id) })
                        }
                    }
                }
            }
        }
}
