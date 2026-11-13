// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.prauga.expense.backup.Backups
import org.prauga.expense.capture.Capture
import org.prauga.expense.core.data.LedgerDatabase
import org.prauga.expense.core.data.LedgerRepository
import org.prauga.expense.core.data.SeedNames
import org.prauga.expense.core.data.SettingsStore
import org.prauga.expense.widget.SafeToSpendWidget

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository by lazy { LedgerRepository(LedgerDatabase.open(app).dao()) }
    val settings = SettingsStore(app)
    val backups by lazy { Backups(app, repository, settings) }
    val capture by lazy { Capture(app, repository, settings) }

    fun start() {
        scope.launch {
            val r = app.resources
            repository.seedIfEmpty(
                settings.now().baseCurrency,
                SeedNames(
                    r.getString(R.string.seed_cash),
                    r.getStringArray(R.array.seed_expense).toList(),
                    r.getStringArray(R.array.seed_income).toList(),
                ),
            )
        }
        scope.launch {
            repository.data
                .map { it.transactions.size to it.transactions.firstOrNull()?.updatedAt }
                .distinctUntilChanged()
                .collect { SafeToSpendWidget.refresh(app) }
        }
    }
}
