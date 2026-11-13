// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.launch
import org.prauga.expense.LedgerApplication
import org.prauga.expense.core.capture.Sources

class PaymentNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in Sources.PAYMENT_APPS || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val text =
            listOfNotNull(
                    extras.getCharSequence(Notification.EXTRA_TITLE),
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?: extras.getCharSequence(Notification.EXTRA_TEXT),
                )
                .joinToString(". ")
        if (text.isBlank()) return
        val container = (application as LedgerApplication).container
        container.scope.launch {
            container.capture.fromNotification(sbn.packageName, text, sbn.postTime)
        }
    }
}
