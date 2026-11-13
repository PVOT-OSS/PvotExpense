// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.AccountType
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Frequency
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxSource
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType

class LedgerTest {
    private val zone = ZoneOffset.UTC
    private val fx = Converter { minor, _ -> minor }
    private val bank =
        Account(
            id = "bank",
            name = "Bank",
            type = AccountType.BANK,
            currency = "INR",
            openingMinor = 100_000_00,
        )
    private val cash =
        Account(id = "cash", name = "Cash", type = AccountType.CASH, currency = "INR")
    private val card =
        Account(id = "card", name = "Card", type = AccountType.CARD, currency = "INR")

    private fun at(date: String) =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000

    private fun expense(
        amount: Long,
        date: String,
        account: String = "bank",
        category: String? = "food",
    ) =
        Transaction(
            type = TxType.EXPENSE,
            occurredAt = at(date),
            accountId = account,
            amountMinor = amount,
            currency = "INR",
            categoryId = category,
        )

    @Test
    fun balancesFollowTransfersAndIgnorePending() {
        val txs =
            listOf(
                expense(500_00, "2026-11-02"),
                Transaction(
                    type = TxType.TRANSFER,
                    occurredAt = at("2026-11-03"),
                    accountId = "bank",
                    toAccountId = "cash",
                    amountMinor = 2_000_00,
                    toAmountMinor = 2_000_00,
                    currency = "INR",
                ),
                expense(300_00, "2026-11-04", account = "cash"),
                expense(9_999_00, "2026-11-05").copy(status = TxStatus.PENDING),
                expense(1_000_00, "2026-11-06", account = "card"),
            )
        val b = balances(listOf(bank, cash, card), txs)
        assertEquals(97_500_00L, b["bank"])
        assertEquals(1_700_00L, b["cash"])
        assertEquals(-1_000_00L, b["card"])
        assertEquals(98_200_00L, netWorth(listOf(bank, cash, card), txs, fx))
    }

    @Test
    fun driftShowsWhatTheBankKnowsAndWeDont() {
        val txs = listOf(expense(500_00, "2026-11-02"), expense(200_00, "2026-11-10"))
        assertEquals(-300_00L, drift(bank, txs, at("2026-11-05"), 99_200_00))
    }

    @Test
    fun periodsStartOnTheChosenDay() {
        val p = Period.containing(LocalDate.parse("2026-11-13"), 25)
        assertEquals(LocalDate.parse("2026-10-25"), p.start)
        assertEquals(LocalDate.parse("2026-11-25"), p.endExclusive)
        assertEquals(12, p.daysLeft(LocalDate.parse("2026-11-13")))
        assertEquals(LocalDate.parse("2026-09-25"), p.previous(25).start)
    }

    @Test
    fun monthlyScheduleKeepsTheDay() {
        val s =
            Schedule(
                type = TxType.EXPENSE,
                accountId = "bank",
                amountMinor = 1,
                currency = "INR",
                payee = "Rent",
                frequency = Frequency.MONTHLY,
                nextDue = LocalDate.parse("2026-11-01").toEpochDay(),
            )
        assertEquals(
            listOf("2026-11-01", "2026-12-01", "2027-01-01").map(LocalDate::parse),
            occurrences(s, LocalDate.parse("2027-01-15")),
        )
    }

    @Test
    fun budgetsRollOverLeftovers() {
        val txs = listOf(expense(3_000_00, "2026-10-10"), expense(1_000_00, "2026-11-05"))
        val period = Period.containing(LocalDate.parse("2026-11-13"), 1)
        val status =
            budgetStatus(
                    listOf(Budget(categoryId = "food", limitMinor = 5_000_00, rollover = true)),
                    txs,
                    period,
                    1,
                    zone,
                    fx,
                )
                .single()
        assertEquals(7_000_00L, status.limitMinor)
        assertEquals(6_000_00L, status.leftMinor)
    }

    @Test
    fun safeToSpendSubtractsBillsAndRespectsBudgets() {
        val today = LocalDate.parse("2026-11-21")
        val rent =
            Schedule(
                type = TxType.EXPENSE,
                accountId = "bank",
                amountMinor = 20_000_00,
                currency = "INR",
                payee = "Rent",
                nextDue = LocalDate.parse("2026-11-28").toEpochDay(),
            )
        val noBudget =
            safeToSpend(listOf(bank), emptyList(), listOf(rent), emptyList(), today, 1, zone, fx)
        assertEquals(10, noBudget.daysLeft)
        assertEquals(8_000_00L, noBudget.perDayMinor)
        assertNull(noBudget.budgetLeftMinor)
        val budget = Budget(categoryId = "food", limitMinor = 10_000_00)
        val withBudget =
            safeToSpend(
                listOf(bank),
                listOf(expense(5_000_00, "2026-11-02")),
                listOf(rent),
                listOf(budget),
                today,
                1,
                zone,
                fx,
            )
        assertEquals(500_00L, withBudget.perDayMinor)
    }

    @Test
    fun dedupesTheSamePaymentFromTwoSources() {
        val sms =
            expense(250_00, "2026-11-02").copy(accountHint = "1234", reference = null, payee = "")
        val app =
            sms.copy(
                id = "other",
                occurredAt = sms.occurredAt + 60_000,
                accountHint = null,
                reference = "512345678901",
                payee = "Swiggy",
            )
        assertTrue(sameMoneyMovement(sms, app))
        assertEquals("Swiggy", merge(sms, app).payee)
        assertFalse(sameMoneyMovement(sms, app.copy(amountMinor = 251_00)))
        assertFalse(sameMoneyMovement(sms, app.copy(accountHint = "9999")))
        assertFalse(sameMoneyMovement(sms, app.copy(occurredAt = sms.occurredAt + 3_600_000)))
    }

    @Test
    fun adjustmentsAreNotSpending() {
        val adj = expense(1_000_00, "2026-11-05").copy(source = TxSource.ADJUSTMENT)
        assertEquals(emptyMap<String?, Long>(), spendByCategory(listOf(adj), fx))
        assertEquals(99_000_00L, balances(listOf(bank), listOf(adj))["bank"])
    }

    @Test
    fun flagsUnusualSpending() {
        val txs =
            listOf(
                expense(1_000_00, "2026-08-10"),
                expense(1_000_00, "2026-09-10"),
                expense(1_000_00, "2026-10-10"),
                expense(3_000_00, "2026-11-10"),
            )
        val i =
            categoryInsights(txs, Period.containing(LocalDate.parse("2026-11-13"), 1), 1, zone, fx)
                .single()
        assertEquals(1_000_00L, i.averageMinor)
        assertTrue(i.unusual)
    }
}
