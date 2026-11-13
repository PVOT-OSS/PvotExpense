// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import org.prauga.expense.R
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.components.SubScreen

@Composable
fun RatesScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    var extra by remember { mutableStateOf(listOf<String>()) }
    var picking by remember { mutableStateOf(false) }
    val currencies =
        (state.data.accounts.map { it.currency } + state.data.rates.map { it.currency } + extra)
            .distinct()
            .filter { it != state.base }
            .sorted()
    SubScreen(stringResource(R.string.exchange_rates), onBack) {
        item { Caption(Formats.currencyName(state.base)) }
        currencies.forEach { c ->
            item(c) {
                val saved = state.data.rates.firstOrNull { it.currency == c }?.perBase.orEmpty()
                var text by remember(saved) { mutableStateOf(saved) }
                PvotTextField(
                    text,
                    { v ->
                        text = v
                        if (v.toBigDecimalOrNull()?.signum() == 1) vm.saveRate(c, v)
                        else if (v.isEmpty()) vm.deleteRate(c)
                    },
                    label = stringResource(R.string.rate_hint, c, state.base),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            PvotButton(
                stringResource(R.string.add_rate),
                onClick = { picking = true },
                style = PvotButtonStyle.Tonal,
            )
        }
    }
    PickerSheet(
        picking,
        stringResource(R.string.currency),
        CURRENCIES.filter { it != state.base && it !in currencies }
            .map { Choice(it, it, Formats.currencyName(it)) },
        onPick = { extra = extra + it },
        onDismiss = { picking = false },
    )
}
