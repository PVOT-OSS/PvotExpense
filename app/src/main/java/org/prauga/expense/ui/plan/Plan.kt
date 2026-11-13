// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgress
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgressColors
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgressDefaults
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.LocalDate
import org.prauga.expense.R
import org.prauga.expense.core.ledger.BudgetStatus
import org.prauga.expense.core.ledger.Upcoming
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.CategoryKind
import org.prauga.expense.core.model.Frequency
import org.prauga.expense.core.model.Money
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.Nav
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.Heading
import org.prauga.expense.ui.components.PickerField
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.home.UpcomingRow
import org.prauga.expense.ui.money

/** Which budget the dialog edits; a [Budget] with an empty category id is a new one. */
class PlanDialogs {
    var budget by mutableStateOf<Budget?>(null)
}

fun LazyListScope.planContent(state: AppState, nav: Nav, dialogs: PlanDialogs) {
    item("b-h") { Heading(stringResource(R.string.budgets)) }
    if (state.budgets.isEmpty()) item("b-empty") { Caption(stringResource(R.string.empty_budgets)) }
    else
        item("b") {
            PvotListGroup {
                state.budgets.forEach { b ->
                    item { BudgetRow(b, state) { dialogs.budget = b.budget } }
                }
            }
        }
    item("b-add") {
        PvotButton(
            stringResource(R.string.add_budget),
            onClick = { dialogs.budget = Budget(categoryId = "", limitMinor = 0) },
            style = PvotButtonStyle.Tonal,
        )
    }

    item("s-h") { Heading(stringResource(R.string.bills)) }
    val schedules = state.data.schedules
    if (schedules.isEmpty()) item("s-empty") { Caption(stringResource(R.string.empty_schedules)) }
    else {
        item("s") {
            PvotListGroup {
                schedules.forEach { s ->
                    item {
                        UpcomingRow(Upcoming(s, LocalDate.ofEpochDay(s.nextDue)), state.today) {
                            nav.schedule(s.id)
                        }
                    }
                }
            }
        }
        val yearly =
            schedules
                .filter { it.active && it.type == TxType.EXPENSE }
                .sumOf { s ->
                    val perYear =
                        when (s.frequency) {
                            Frequency.WEEKLY -> 52 / s.interval.coerceAtLeast(1)
                            Frequency.MONTHLY -> 12 / s.interval.coerceAtLeast(1)
                            Frequency.YEARLY -> 1
                        }
                    state.fx.toBase(s.amountMinor, s.currency) * perYear
                }
        if (yearly > 0)
            item("s-cost") {
                Caption(stringResource(R.string.subscriptions_cost, money(yearly, state.base)))
            }
    }
    item("s-add") {
        PvotButton(
            stringResource(R.string.add_schedule),
            onClick = { nav.schedule(null) },
            style = PvotButtonStyle.Tonal,
        )
    }
}

@Composable
private fun BudgetRow(status: BudgetStatus, state: AppState, onClick: () -> Unit) {
    val name =
        state.data.category(status.budget.categoryId)?.name ?: stringResource(R.string.no_category)
    val over = status.leftMinor < 0
    PvotListRow(
        title = { Text(name) },
        supportingText = {
            Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS)) {
                PvotLinearProgress(
                    progress = { status.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.XS),
                    colors =
                        if (over)
                            PvotLinearProgressColors(
                                indicatorColor = MaterialTheme.colorScheme.error,
                                trackColor =
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = PvotLinearProgressDefaults.TrackAlpha
                                    ),
                            )
                        else PvotLinearProgressDefaults.colors(),
                )
                Text(
                    if (over)
                        stringResource(
                            R.string.budget_over,
                            money(-status.leftMinor, state.base),
                            money(status.limitMinor, state.base),
                        )
                    else
                        stringResource(
                            R.string.budget_left,
                            money(status.leftMinor, state.base),
                            money(status.limitMinor, state.base),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (over) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
fun BudgetDialog(dialogs: PlanDialogs, state: AppState, vm: AppViewModel) {
    val editing = dialogs.budget
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var rollover by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        editing?.let {
            category = it.categoryId
            amount = if (it.limitMinor > 0) Formats.plain(it.limitMinor, state.base) else ""
            rollover = it.rollover
        }
    }
    val close = { dialogs.budget = null }
    val choices =
        state.data.categories
            .filter {
                it.kind == CategoryKind.EXPENSE &&
                    (it.id == editing?.categoryId ||
                        state.data.budgets.none { b -> b.categoryId == it.id })
            }
            .map { Choice(it.id, it.name) }
    PvotDialog(
        visible = editing != null,
        onDismissRequest = close,
        title = stringResource(R.string.edit_budget),
        content = {
            PickerField(
                stringResource(R.string.category),
                state.data.category(category)?.name ?: stringResource(R.string.none),
            ) {
                picking = true
            }
            PvotTextField(
                value = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.budget_limit),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            PvotSwitchRow(
                title = stringResource(R.string.rollover),
                summary = stringResource(R.string.rollover_summary),
                checked = rollover,
                onCheckedChange = { rollover = it },
            )
        },
    ) {
        val limit = Money.parse(amount, state.base)?.minor ?: 0
        PvotDialogAction(
            stringResource(R.string.save),
            onClick = {
                editing?.let {
                    vm.save(it.copy(categoryId = category, limitMinor = limit, rollover = rollover))
                }
                close()
            },
            style = PvotDialogActionStyle.Primary,
            enabled = category.isNotEmpty() && limit > 0,
        )
        if (editing?.categoryId?.isNotEmpty() == true) {
            PvotDialogAction(
                stringResource(R.string.delete),
                onClick = {
                    vm.delete(editing)
                    close()
                },
                style = PvotDialogActionStyle.Destructive,
            )
        }
        PvotDialogAction(stringResource(R.string.cancel), onClick = close)
    }
    PickerSheet(
        picking,
        stringResource(R.string.category),
        choices,
        onPick = { category = it },
        onDismiss = { picking = false },
    )
}

@Composable
fun FrequencyLabel(f: Frequency): String =
    stringResource(
        when (f) {
            Frequency.WEEKLY -> R.string.weekly
            Frequency.MONTHLY -> R.string.monthly
            Frequency.YEARLY -> R.string.yearly
        }
    )
