// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

object Sources {
    /** Payment and bank apps whose notifications are read; everything else is ignored. */
    val PAYMENT_APPS =
        setOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.dreamplug.androidapp",
            "in.amazon.mShop.android.shopping",
            "com.mobikwik_new",
            "com.freecharge.android",
            "money.jupiter",
            "com.csam.icici.bank.imobile",
            "com.snapwork.hdfc",
            "com.sbi.lotusintouch",
            "com.axis.mobile",
            "com.msf.kbank.mobile",
            "com.fi.money",
            "com.naviapp",
            "com.whatsapp.w4b",
        )

    private val BANK_SENDER = Regex("""^([A-Z]{2}-)?[A-Z0-9]{5,9}(-[A-Z])?$""")

    /** Banks send from alphanumeric headers like "AX-HDFCBK"; a phone number is a person. */
    fun isBankSender(address: String?): Boolean {
        val a = address?.trim()?.uppercase() ?: return false
        return BANK_SENDER.matches(a) && a.any { it.isLetter() }
    }
}
