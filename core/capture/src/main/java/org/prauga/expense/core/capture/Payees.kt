// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import java.util.Locale

object Payees {
    private val NOISE =
        Regex(
            """\b(upi|pvt|private|ltd|limited|llp|inc|india|technologies|tech|services|payments?|retail|online|store|www|com|in)\b"""
        )

    /**
     * A key that stays the same across the ways one merchant gets written ("SWIGGY*ORDER 123",
     * "swiggy").
     */
    fun key(text: String): String =
        text
            .lowercase(Locale.ROOT)
            .substringBefore('@')
            .substringBefore('*')
            .replace(Regex("""\d{3,}"""), " ")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(NOISE, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** "SWIGGY" → "Swiggy", "uber" → "Uber"; mixed case is left as the merchant wrote it. */
    fun display(text: String): String {
        val t = text.replace(Regex("""\s+"""), " ").trim()
        val shouting = t.none { it.isLowerCase() } || t.none { it.isUpperCase() }
        return if (!shouting) t
        else
            t.lowercase(Locale.ROOT).split(' ').joinToString(" ") { w ->
                w.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
    }
}
