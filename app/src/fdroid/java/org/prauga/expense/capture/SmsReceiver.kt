// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.launch
import org.prauga.expense.LedgerApplication

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = parts.firstOrNull() ?: return
        val sms =
            Sms(
                first.originatingAddress,
                parts.joinToString("") { it.messageBody.orEmpty() },
                first.timestampMillis,
            )
        val container = (context.applicationContext as LedgerApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                if (container.settings.now().captureSms) container.capture.fromSms(sms)
            } finally {
                pending.finish()
            }
        }
    }
}
