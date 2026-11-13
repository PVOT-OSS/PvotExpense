// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun usesTheCurrencyExponent() {
        assertEquals(12345L, Money.parse("123.45", "INR")!!.minor)
        assertEquals(123L, Money.parse("123", "JPY")!!.minor)
        assertEquals(123456L, Money.parse("123.456", "KWD")!!.minor)
    }

    @Test
    fun parsesGroupedNumbers() {
        assertEquals(12345678L, Money.parse("1,23,456.78", "INR")!!.minor)
        assertNull(Money.parse("abc", "INR"))
    }

    @Test
    fun roundsHalfEven() {
        assertEquals(2L, Money.of(BigDecimal("0.025"), "USD").minor)
        assertEquals(4L, Money.of(BigDecimal("0.035"), "USD").minor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToMixCurrencies() {
        Money(1, "INR") + Money(1, "USD")
    }
}
