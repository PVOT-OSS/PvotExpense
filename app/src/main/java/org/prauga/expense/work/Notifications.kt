// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import org.prauga.expense.MainActivity
import org.prauga.expense.R
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.ui.Formats

object Notifications {
    private const val BILLS = "bills"
    private const val INBOX = "inbox"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                BILLS,
                context.getString(R.string.channel_bills),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                INBOX,
                context.getString(R.string.channel_inbox),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun open(context: Context, action: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            action.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33

    fun captured(context: Context, tx: Transaction) {
        if (!allowed(context)) return
        val n =
            NotificationCompat.Builder(context, INBOX)
                .setSmallIcon(R.drawable.ic_inbox)
                .setContentTitle(
                    context
                        .getString(
                            R.string.inbox_notification,
                            Formats.money(tx.amountMinor, tx.currency),
                            tx.payee,
                        )
                        .trim()
                )
                .setContentText(context.getString(R.string.inbox_notification_text))
                .setContentIntent(open(context, MainActivity.ACTION_INBOX))
                .setAutoCancel(true)
                .setGroup(INBOX)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(tx.id.hashCode(), n) }
    }

    fun billDue(context: Context, schedule: Schedule, due: LocalDate) {
        if (!allowed(context)) return
        val n =
            NotificationCompat.Builder(context, BILLS)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(
                    context.getString(R.string.bill_due, schedule.payee, Formats.shortDate(due))
                )
                .setContentText(Formats.money(schedule.amountMinor, schedule.currency))
                .setContentIntent(open(context, MainActivity.ACTION_PLAN))
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(schedule.id.hashCode(), n) }
    }
}
