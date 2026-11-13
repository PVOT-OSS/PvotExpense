// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import java.math.BigDecimal
import org.prauga.expense.core.model.Money

internal const val CURRENCY = """(₹|rs\.?|inr|usd|us\$|\$|eur|€|gbp|£|aed|sgd|jpy|¥)"""
internal const val NUMBER = """([0-9][0-9,]*(?:\.[0-9]{1,3})?)"""

internal fun currencyCode(symbol: String?, fallback: String): String =
    when (symbol?.lowercase()?.trimEnd('.')) {
        null -> fallback
        "₹",
        "rs",
        "inr" -> "INR"
        "$",
        "usd",
        "us$" -> "USD"
        "€",
        "eur" -> "EUR"
        "£",
        "gbp" -> "GBP"
        "aed" -> "AED"
        "sgd" -> "SGD"
        "¥",
        "jpy" -> "JPY"
        else -> fallback
    }

internal fun amountMinor(number: String, currency: String): Long? =
    number
        .replace(",", "")
        .toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?.let { Money.of(it, currency).minor }
