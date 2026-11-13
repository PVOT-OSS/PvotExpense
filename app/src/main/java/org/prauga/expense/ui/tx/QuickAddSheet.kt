// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.tx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.Instant
import kotlinx.coroutines.delay
import org.prauga.expense.R
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.dayLabel
import org.prauga.expense.ui.money

@Composable
fun QuickAddSheet(
    visible: Boolean,
    state: AppState,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onDetails: (Transaction?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val draft =
        remember(text, state.data) { if (text.isBlank()) null else vm.resolve(vm.parse(text)) }
    val close = {
        text = ""
        onDismiss()
    }
    fun add() {
        if (vm.quickAdd(text)) close()
    }
    PvotBottomSheet(
        visible = visible,
        onDismissRequest = close,
        title = stringResource(R.string.quick_add),
    ) {
        LaunchedEffect(visible) {
            if (visible) {
                delay(150)
                runCatching { focus.requestFocus() }
            }
        }
        Column(
            Modifier.padding(horizontal = PvotSpacing.L).padding(bottom = PvotSpacing.L),
            verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
        ) {
            PvotTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.quick_add_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Text(
                preview(draft, state, text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.quick_add_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
                PvotButton(stringResource(R.string.add), onClick = ::add, enabled = draft != null)
                PvotButton(
                    stringResource(R.string.quick_add_more),
                    onClick = {
                        onDetails(draft)
                        text = ""
                    },
                    style = PvotButtonStyle.Tonal,
                )
            }
        }
    }
}

@Composable
private fun preview(draft: Transaction?, state: AppState, text: String): String {
    if (draft == null)
        return if (text.isBlank()) "" else stringResource(R.string.quick_add_no_amount)
    val parts =
        mutableListOf(
            money(draft.amountMinor, draft.currency, signed = draft.type == TxType.INCOME)
        )
    when (draft.type) {
        TxType.TRANSFER ->
            parts +=
                "${state.data.account(draft.accountId)?.name} → ${state.data.account(draft.toAccountId)?.name}"
        else -> {
            parts +=
                state.data.category(draft.categoryId)?.name ?: stringResource(R.string.no_category)
            state.data.account(draft.accountId)?.let { parts += it.name }
        }
    }
    if (draft.payee.isNotBlank()) parts.add(1, draft.payee)
    parts +=
        dayLabel(
            Instant.ofEpochMilli(draft.occurredAt).atZone(state.zone).toLocalDate(),
            state.today,
        )
    return parts.joinToString(" · ")
}
