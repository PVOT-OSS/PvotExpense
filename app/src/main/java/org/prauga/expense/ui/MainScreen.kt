// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotScreenScaffold
import com.prauga.pvot.designsystem.components.button.PvotFab
import com.prauga.pvot.designsystem.navigation.PvotNavBar
import com.prauga.pvot.designsystem.navigation.PvotTabItem
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.expense.R
import org.prauga.expense.ui.activity.ActivityFilter
import org.prauga.expense.ui.activity.activityContent
import org.prauga.expense.ui.home.homeContent
import org.prauga.expense.ui.insights.InsightsState
import org.prauga.expense.ui.insights.insightsContent
import org.prauga.expense.ui.plan.PlanDialogs
import org.prauga.expense.ui.plan.planContent

enum class Tab(val icon: Int, val label: Int) {
    HOME(R.drawable.ic_home, R.string.tab_home),
    ACTIVITY(R.drawable.ic_list, R.string.tab_activity),
    PLAN(R.drawable.ic_target, R.string.tab_plan),
    INSIGHTS(R.drawable.ic_chart, R.string.tab_insights),
}

private val TABS = Tab.entries.map { PvotTabItem(it.icon, it.label, it.label) }

@Composable
fun MainScreen(
    vm: AppViewModel,
    state: AppState,
    nav: Nav,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onQuickAdd: () -> Unit,
    activity: ActivityFilter,
    onActivity: (ActivityFilter) -> Unit,
    insights: InsightsState,
    onInsights: (InsightsState) -> Unit,
    plan: PlanDialogs,
) {
    val list = rememberLazyListState()
    val compact by remember {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pending = state.data.pending.size
    PvotScreenScaffold(
        topBar = {
            PvotTopBar(
                title =
                    if (tab == Tab.HOME) stringResource(R.string.app_name)
                    else stringResource(tab.label),
                compact = compact,
                actions = {
                    if (pending > 0)
                        PvotTopBarAction(
                            painterResource(R.drawable.ic_inbox),
                            stringResource(R.string.inbox),
                            nav::inbox,
                        )
                    PvotTopBarAction(
                        painterResource(R.drawable.ic_settings),
                        stringResource(R.string.settings),
                        nav::settings,
                    )
                },
            )
        },
        bottomBar = {
            PvotNavBar(
                selectedTab = tab.ordinal,
                onTabClick = { onTab(Tab.entries[it]) },
                tabs = TABS,
                compact = compact,
            )
        },
        floatingAction = {
            if (tab == Tab.HOME || tab == Tab.ACTIVITY) {
                PvotFab(
                    painterResource(R.drawable.ic_plus),
                    onClick = onQuickAdd,
                    contentDescription = stringResource(R.string.add),
                    text = stringResource(R.string.add),
                    expanded = !compact,
                )
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            PvotScreen(
                state = list,
                contentPadding =
                    PvotScreenDefaults.contentPadding(
                        top = padding.calculateTopPadding() + PvotSpacing.S,
                        bottom = bottom + 160.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
                horizontalAlignment = Alignment.Start,
            ) {
                val content: LazyListScope.() -> Unit =
                    when (tab) {
                        Tab.HOME -> {
                            { homeContent(vm, state, nav, onSeeAll = { onTab(Tab.ACTIVITY) }) }
                        }
                        Tab.ACTIVITY -> {
                            { activityContent(state, nav, activity, onActivity) }
                        }
                        Tab.PLAN -> {
                            { planContent(state, nav, plan) }
                        }
                        Tab.INSIGHTS -> {
                            { insightsContent(state, insights, onInsights) }
                        }
                    }
                content()
            }
        }
    }
}
