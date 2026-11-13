// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotScreenScaffold
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.theme.PvotSpacing

@Composable
fun SubScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: @Composable () -> Unit = {},
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PvotSpacing.M),
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val compact by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PvotScreenScaffold(
        topBar = { PvotTopBar(title, onBack = onBack, compact = compact, actions = actions) },
        floatingAction = floatingAction,
    ) { padding ->
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            PvotScreen(
                state = state,
                contentPadding =
                    PvotScreenDefaults.contentPadding(
                        top = padding.calculateTopPadding() + PvotSpacing.S,
                        bottom = bottom + 96.dp,
                    ),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = Alignment.Start,
                content = content,
            )
        }
    }
}
