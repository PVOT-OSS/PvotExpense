// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.capture

import android.content.Context
import androidx.core.net.toUri

/**
 * This build never holds an SMS permission. Bank SMS come from the Messages app, which shares only
 * messages from bank senders with apps it trusts.
 */
object SmsSource {
    const val VIA_MESSAGES = true
    private const val AUTHORITY = "org.prauga.messages.transactions"
    const val PERMISSION = "org.prauga.messages.permission.READ_TRANSACTION_SMS"
    val PERMISSIONS = arrayOf(PERMISSION)

    fun available(context: Context): Boolean =
        context.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    fun read(context: Context, since: Long): List<Sms> {
        if (!available(context)) return emptyList()
        val out = mutableListOf<Sms>()
        runCatching {
            context.contentResolver
                .query(
                    "content://$AUTHORITY/sms".toUri(),
                    arrayOf("address", "body", "date"),
                    "date > ?",
                    arrayOf(since.toString()),
                    "date ASC",
                )
                ?.use { c ->
                    while (c.moveToNext()) out +=
                        Sms(c.getString(0), c.getString(1).orEmpty(), c.getLong(2))
                }
        }
        return out
    }
}
