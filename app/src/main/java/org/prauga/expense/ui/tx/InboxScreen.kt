// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.tx

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.designsystem.components.list.PvotSwipeAction
import com.prauga.pvot.designsystem.components.list.PvotSwipeRow
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import org.prauga.expense.R
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.SubScreen
import org.prauga.expense.ui.components.TxRow

@Composable
fun InboxScreen(state: AppState, vm: AppViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val pending = state.data.pending
    SubScreen(
        title = stringResource(R.string.inbox),
        onBack = onBack,
        actions = {
            if (pending.isNotEmpty())
                PvotTopBarAction(
                    painterResource(R.drawable.ic_check),
                    stringResource(R.string.confirm_all),
                    vm::confirmAll,
                )
        },
    ) {
        item { Caption(stringResource(R.string.inbox_explained)) }
        if (pending.isEmpty()) {
            item {
                PvotEmptyContent(
                    message = stringResource(R.string.empty_inbox),
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    icon = { Icon(painterResource(R.drawable.ic_inbox), contentDescription = null) },
                )
            }
        }
        items(pending, key = { it.id }) { tx ->
            PvotSwipeRow(
                modifier = Modifier.animateItem(),
                startAction =
                    PvotSwipeAction(
                        label = stringResource(R.string.confirm),
                        icon = painterResource(R.drawable.ic_check),
                        onSwipe = { vm.confirm(tx) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        removesRow = true,
                    ),
                endAction =
                    PvotSwipeAction(
                        label = stringResource(R.string.dismiss),
                        icon = painterResource(R.drawable.ic_delete),
                        onSwipe = { vm.delete(tx) },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        removesRow = true,
                    ),
            ) {
                TxRow(tx, state.data, state.zone, onClick = { onOpen(tx.id) }, showDate = true)
            }
        }
    }
}
