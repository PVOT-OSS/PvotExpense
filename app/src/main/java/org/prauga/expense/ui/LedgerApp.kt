// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.prauga.pvot.designsystem.components.PvotSnackbarHost
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.prauga.expense.R
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.ui.accounts.AccountScreen
import org.prauga.expense.ui.accounts.AccountsScreen
import org.prauga.expense.ui.accounts.EditAccountScreen
import org.prauga.expense.ui.activity.ActivityFilter
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.insights.InsightsState
import org.prauga.expense.ui.plan.BudgetDialog
import org.prauga.expense.ui.plan.PlanDialogs
import org.prauga.expense.ui.plan.ScheduleScreen
import org.prauga.expense.ui.settings.AboutScreen
import org.prauga.expense.ui.settings.CURRENCIES
import org.prauga.expense.ui.settings.CategoriesScreen
import org.prauga.expense.ui.settings.RatesScreen
import org.prauga.expense.ui.settings.SettingsScreen
import org.prauga.expense.ui.tx.InboxScreen
import org.prauga.expense.ui.tx.QuickAddSheet
import org.prauga.expense.ui.tx.TransactionScreen

@Serializable data object MainKey : NavKey

@Serializable data class TxKey(val id: String?, val draft: Transaction? = null) : NavKey

@Serializable data object InboxKey : NavKey

@Serializable data object AccountsKey : NavKey

@Serializable data class AccountKey(val id: String) : NavKey

@Serializable data class EditAccountKey(val id: String?) : NavKey

@Serializable data object CategoriesKey : NavKey

@Serializable data class ScheduleKey(val id: String?) : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data object RatesKey : NavKey

@Serializable data object AboutKey : NavKey

/** What the launching intent asked for: a quick add, the inbox or the plan. */
enum class Launch {
    NONE,
    QUICK_ADD,
    INBOX,
    PLAN,
}

@Composable
fun LedgerApp(vm: AppViewModel, launch: StateFlow<Launch>, onLaunchHandled: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    if (!state.settings.onboarded) {
        Onboarding(state, vm)
        return
    }
    val backStack = rememberNavBackStack(MainKey)
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var activity by remember { mutableStateOf(ActivityFilter()) }
    var insights by remember { mutableStateOf(InsightsState()) }
    val plan = remember { PlanDialogs() }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val pendingLaunch by launch.collectAsStateWithLifecycle()

    LaunchedEffect(pendingLaunch) {
        when (pendingLaunch) {
            Launch.QUICK_ADD -> quickAdd = true
            Launch.INBOX -> if (backStack.lastOrNull() != InboxKey) backStack.add(InboxKey)
            Launch.PLAN -> tab = Tab.PLAN
            Launch.NONE -> return@LaunchedEffect
        }
        onLaunchHandled()
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val text =
                m.count?.let { resources.getQuantityString(m.text, it, *m.args.toTypedArray()) }
                    ?: resources.getString(m.text, *m.args.toTypedArray())
            val result =
                snackbar.showSnackbar(
                    text,
                    actionLabel = m.undo?.let { resources.getString(R.string.undo) },
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) m.undo?.invoke()
        }
    }

    val nav =
        object : Nav {
            override fun back() {
                backStack.removeLastOrNull()
            }

            override fun transaction(id: String?) {
                backStack.add(TxKey(id))
            }

            override fun inbox() {
                backStack.add(InboxKey)
            }

            override fun accounts() {
                backStack.add(AccountsKey)
            }

            override fun account(id: String) {
                backStack.add(AccountKey(id))
            }

            override fun editAccount(id: String?) {
                backStack.add(EditAccountKey(id))
            }

            override fun categories() {
                backStack.add(CategoriesKey)
            }

            override fun schedule(id: String?) {
                backStack.add(ScheduleKey(id))
            }

            override fun settings() {
                backStack.add(SettingsKey)
            }

            override fun rates() {
                backStack.add(RatesKey)
            }

            override fun about() {
                backStack.add(AboutKey)
            }
        }

    CompositionLocalProvider(LocalHideAmounts provides state.settings.hideAmounts) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        entry<MainKey> {
                            MainScreen(
                                vm,
                                state,
                                nav,
                                tab,
                                { tab = it },
                                { quickAdd = true },
                                activity,
                                { activity = it },
                                insights,
                                { insights = it },
                                plan,
                            )
                        }
                        entry<TxKey> { k -> TransactionScreen(k.id, k.draft, state, vm, nav::back) }
                        entry<InboxKey> {
                            InboxScreen(state, vm, onOpen = nav::transaction, onBack = nav::back)
                        }
                        entry<AccountsKey> { AccountsScreen(state, nav) }
                        entry<AccountKey> { k -> AccountScreen(k.id, state, vm, nav) }
                        entry<EditAccountKey> { k ->
                            EditAccountScreen(
                                k.id,
                                state,
                                vm,
                                onBack = nav::back,
                                onDeleted = {
                                    backStack.removeLastOrNull()
                                    if (backStack.lastOrNull() is AccountKey)
                                        backStack.removeLastOrNull()
                                },
                            )
                        }
                        entry<CategoriesKey> { CategoriesScreen(state, vm, nav::back) }
                        entry<ScheduleKey> { k -> ScheduleScreen(k.id, state, vm, nav::back) }
                        entry<SettingsKey> { SettingsScreen(state, vm, nav) }
                        entry<RatesKey> { RatesScreen(state, vm, nav::back) }
                        entry<AboutKey> { AboutScreen(nav::back) }
                    },
            )
            PvotSnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
            )
        }
        QuickAddSheet(
            quickAdd,
            state,
            vm,
            onDismiss = { quickAdd = false },
            onDetails = { draft ->
                quickAdd = false
                backStack.add(TxKey(null, draft))
            },
        )
        BudgetDialog(plan, state, vm)
    }
}

@Composable
private fun Onboarding(state: AppState, vm: AppViewModel) {
    var currency by rememberSaveable { mutableStateOf(state.settings.baseCurrency) }
    var picking by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(PvotSpacing.L),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PvotSpacing.L)) {
            Text(
                stringResource(R.string.welcome),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PvotButton(
                "${stringResource(R.string.base_currency)}: $currency",
                onClick = { picking = true },
                style = PvotButtonStyle.Tonal,
            )
            PvotButton(
                stringResource(R.string.get_started),
                onClick = { vm.setOnboarded(currency) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    PickerSheet(
        picking,
        stringResource(R.string.base_currency),
        (listOf(currency) + CURRENCIES).distinct().map { Choice(it, it, Formats.currencyName(it)) },
        onPick = { currency = it },
        onDismiss = { picking = false },
    )
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(PvotSpacing.L),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PvotSpacing.L),
        ) {
            Text(
                stringResource(R.string.locked),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            PvotButton(stringResource(R.string.unlock), onClick = onUnlock)
        }
    }
}
