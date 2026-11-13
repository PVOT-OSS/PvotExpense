// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.capture

import android.Manifest
import android.content.Context
import android.provider.Telephony

/** This build reads bank SMS itself, with the user's permission. */
object SmsSource {
    const val VIA_MESSAGES = false
    val PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun available(context: Context): Boolean = true

    fun read(context: Context, since: Long): List<Sms> {
        val out = mutableListOf<Sms>()
        runCatching {
            context.contentResolver
                .query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.DATE} > ?",
                    arrayOf(since.toString()),
                    "${Telephony.Sms.DATE} ASC",
                )
                ?.use { c ->
                    while (c.moveToNext()) out +=
                        Sms(c.getString(0), c.getString(1).orEmpty(), c.getLong(2))
                }
        }
        return out
    }
}
