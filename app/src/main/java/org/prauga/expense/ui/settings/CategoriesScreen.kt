// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.list.PvotLetterTile
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import org.prauga.expense.R
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.CategoryKind
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.components.Heading
import org.prauga.expense.ui.components.SubScreen

@Composable
fun CategoriesScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Category?>(null) }
    SubScreen(
        title = stringResource(R.string.categories),
        onBack = onBack,
        actions = {
            PvotTopBarAction(
                painterResource(R.drawable.ic_plus),
                stringResource(R.string.add_category),
                onClick = { editing = Category(name = "", kind = CategoryKind.EXPENSE) },
            )
        },
    ) {
        CategoryKind.entries.forEach { kind ->
            item("h$kind") {
                Heading(
                    stringResource(
                        if (kind == CategoryKind.EXPENSE) R.string.expense else R.string.income
                    )
                )
            }
            item("g$kind") {
                PvotListGroup {
                    state.data.categories
                        .filter { it.kind == kind }
                        .forEach { c ->
                            item {
                                PvotListRow(
                                    title = c.name,
                                    leading = { PvotLetterTile(c.name, colorKey = c.id) },
                                    onClick = { editing = c },
                                )
                            }
                        }
                }
            }
        }
    }
    val current = editing
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    LaunchedEffect(current) {
        name = current?.name.orEmpty()
        kind = current?.kind ?: CategoryKind.EXPENSE
    }
    val isNew = current != null && state.data.categories.none { it.id == current.id }
    PvotDialog(
        visible = current != null,
        onDismissRequest = { editing = null },
        title = stringResource(if (isNew) R.string.add_category else R.string.category),
        content = {
            PvotTextField(
                name,
                { name = it },
                label = stringResource(R.string.category_name),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isNew) {
                PvotSegmentedControl(
                    listOf(stringResource(R.string.expense), stringResource(R.string.income)),
                    kind.ordinal,
                    { kind = CategoryKind.entries[it] },
                    Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        PvotDialogAction(
            stringResource(R.string.save),
            onClick = {
                current?.let {
                    vm.save(
                        it.copy(
                            name = name.trim(),
                            kind = kind,
                            color = if (isNew) state.data.categories.size else it.color,
                        )
                    )
                }
                editing = null
            },
            style = PvotDialogActionStyle.Primary,
            enabled = name.isNotBlank(),
        )
        if (!isNew && current != null)
            PvotDialogAction(
                stringResource(R.string.delete),
                onClick = {
                    vm.delete(current)
                    editing = null
                },
                style = PvotDialogActionStyle.Destructive,
            )
        PvotDialogAction(stringResource(R.string.cancel), onClick = { editing = null })
    }
}
