// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.capture

import android.content.Context
import org.prauga.expense.core.capture.PaymentParser
import org.prauga.expense.core.capture.Sources
import org.prauga.expense.core.data.LedgerRepository
import org.prauga.expense.core.data.SettingsStore
import org.prauga.expense.core.model.TxSource
import org.prauga.expense.work.Notifications

data class Sms(val address: String?, val body: String, val at: Long)

/** Turns notifications, SMS and shared text into inbox entries. */
class Capture(
    private val context: Context,
    private val repository: LedgerRepository,
    private val settings: SettingsStore,
) {
    suspend fun fromNotification(packageName: String, text: String, at: Long) {
        if (!settings.now().captureNotifications || packageName !in Sources.PAYMENT_APPS) return
        file(text, at, TxSource.NOTIFICATION, notify = true)
    }

    suspend fun fromSms(sms: Sms, notify: Boolean = true): Boolean {
        if (!Sources.isBankSender(sms.address)) return false
        return file(sms.body, sms.at, TxSource.SMS, notify)
    }

    suspend fun fromShare(text: String): Boolean =
        file(text, System.currentTimeMillis(), TxSource.SHARE, notify = false)

    private suspend fun file(text: String, at: Long, source: TxSource, notify: Boolean): Boolean {
        val base = settings.now().baseCurrency
        val payment = PaymentParser.parse(text, base) ?: return false
        val tx = repository.capture(payment, at, source, text) ?: return false
        if (notify) Notifications.captured(context, tx)
        return true
    }

    /** Bank SMS from the last 90 days, or since the last read, filed into the inbox. */
    suspend fun importSmsHistory(): Int {
        val since =
            maxOf(settings.smsCursor(), System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
        val messages = SmsSource.read(context, since)
        var found = 0
        for (sms in messages.sortedBy { it.at }) if (fromSms(sms, notify = false)) found++
        messages.maxOfOrNull { it.at }?.let { settings.setSmsCursor(it) }
        return found
    }

    suspend fun pullFromMessages() {
        val s = settings.now()
        if (SmsSource.VIA_MESSAGES && s.captureMessages || !SmsSource.VIA_MESSAGES && s.captureSms)
            importSmsHistory()
    }
}
