// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.prauga.expense.core.model.TxType

class QuickAddParserTest {
    private val today = LocalDate.parse("2026-11-13") // a Friday

    private fun parse(text: String) = QuickAddParser.parse(text, today, "INR")

    @Test
    fun fullLine() {
        val e = parse("250 lunch with ravi #food @hdfc yesterday")
        assertEquals(250_00L, e.amountMinor)
        assertEquals(TxType.EXPENSE, e.type)
        assertEquals("Lunch with ravi", e.payee)
        assertEquals("food", e.categoryHint)
        assertEquals("hdfc", e.accountHint)
        assertEquals(today.minusDays(1), e.date)
    }

    @Test
    fun incomeAndSuffixes() {
        val e = parse("+50k salary")
        assertEquals(50_000_00L, e.amountMinor)
        assertEquals(TxType.INCOME, e.type)
        assertEquals(1_50_000_00L, parse("1.5L car service").amountMinor)
        assertEquals(TxType.INCOME, parse("1200 refund amazon").type)
    }

    @Test
    fun currencies() {
        val e = parse("\$12.50 coffee")
        assertEquals("USD", e.currency)
        assertEquals(12_50L, e.amountMinor)
        assertEquals("EUR", parse("20 eur museum").currency)
        assertEquals("Museum", parse("20 eur museum").payee)
        assertEquals(1_234_00L, parse("₹1,234 groceries").amountMinor)
    }

    @Test
    fun dates() {
        assertEquals(LocalDate.parse("2026-11-03"), parse("100 tea 3 nov").date)
        assertEquals(LocalDate.parse("2025-12-24"), parse("100 gift 24 dec").date)
        assertEquals(LocalDate.parse("2026-11-09"), parse("100 tea mon").date)
        assertEquals(LocalDate.parse("2026-10-05"), parse("100 tea 5/10").date)
    }

    @Test
    fun transfer() {
        val e = parse("2000 @hdfc > @cash")
        assertEquals(TxType.TRANSFER, e.type)
        assertEquals("hdfc", e.accountHint)
        assertEquals("cash", e.toAccountHint)
    }

    @Test fun noAmount() = assertNull(parse("coffee").amountMinor)

    @Test
    fun rulesLearnAndMatchMerchants() {
        assertEquals("swiggy", Payees.key("SWIGGY*ORDER 123456"))
        assertEquals("uber", Payees.key("uber@ybl"))
        val rule = Rules.learn("Swiggy", "food", null, emptyList(), 1)!!
        assertEquals("food", Rules.match("SWIGGY INSTAMART", listOf(rule))?.categoryId)
        assertNull(Rules.match("Zomato", listOf(rule)))
        assertEquals(2, Rules.learn("swiggy", "food", null, listOf(rule), 2)!!.hits)
        assertEquals("Swiggy Instamart", Payees.display("SWIGGY INSTAMART"))
        assertEquals("McDonald's", Payees.display("McDonald's"))
    }
}
