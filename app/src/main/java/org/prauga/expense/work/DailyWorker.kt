// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.prauga.expense.LedgerApplication
import org.prauga.expense.core.model.TxType
import org.prauga.expense.widget.SafeToSpendWidget

/**
 * Twice a day: post due schedules, remind about bills, pull bank SMS, back up, refresh the widget.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LedgerApplication).container
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        container.repository.postDue(today, zone)
        val data = container.repository.data.first()
        data.schedules
            .filter { it.active && it.type == TxType.EXPENSE }
            .forEach { s ->
                val due = LocalDate.ofEpochDay(s.nextDue)
                val days = ChronoUnit.DAYS.between(today, due)
                if (
                    days in 0..s.remindDays.toLong() &&
                        (days == s.remindDays.toLong() || days == 0L)
                )
                    Notifications.billDue(applicationContext, s, due)
            }
        runCatching { container.capture.pullFromMessages() }
        val settings = container.settings.now()
        if (
            settings.backupUri != null &&
                settings.hasPassphrase &&
                System.currentTimeMillis() - settings.lastBackupAt > 20 * 60 * 60 * 1000L
        ) {
            container.backups.backUp()
        }
        SafeToSpendWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "daily",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<DailyWorker>(12, TimeUnit.HOURS).build(),
                )
        }
    }
}
