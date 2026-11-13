// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import kotlinx.serialization.Serializable

@Serializable
data class Money(val minor: Long, val currency: String) : Comparable<Money> {
    val decimal: BigDecimal
        get() = BigDecimal.valueOf(minor, exponentOf(currency))

    operator fun plus(other: Money): Money = Money(minor + sameCurrency(other).minor, currency)

    operator fun minus(other: Money): Money = Money(minor - sameCurrency(other).minor, currency)

    operator fun unaryMinus(): Money = Money(-minor, currency)

    override fun compareTo(other: Money): Int = minor.compareTo(sameCurrency(other).minor)

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) { "$currency and ${other.currency} don't mix" }
        return other
    }

    companion object {
        fun zero(currency: String): Money = Money(0, currency)

        fun of(decimal: BigDecimal, currency: String): Money =
            Money(
                decimal
                    .setScale(exponentOf(currency), RoundingMode.HALF_EVEN)
                    .unscaledValue()
                    .toLong(),
                currency,
            )

        fun parse(text: String, currency: String): Money? =
            text.replace(",", "").trim().toBigDecimalOrNull()?.let { of(it, currency) }

        fun exponentOf(currency: String): Int =
            runCatching { Currency.getInstance(currency).defaultFractionDigits }
                .getOrDefault(2)
                .coerceAtLeast(0)
    }
}

fun Iterable<Money>.sum(currency: String): Money =
    Money(
        sumOf {
            require(it.currency == currency)
            it.minor
        },
        currency,
    )
