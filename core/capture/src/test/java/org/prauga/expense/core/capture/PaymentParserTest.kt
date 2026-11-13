// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.prauga.expense.core.model.TxType

class PaymentParserTest {
    private fun check(
        text: String,
        type: TxType,
        minor: Long,
        payee: String,
        account: String? = null,
        reference: String? = null,
        balance: Long? = null,
        currency: String = "INR",
    ) {
        val p = PaymentParser.parse(text) ?: throw AssertionError("not parsed: $text")
        assertEquals(text, type, p.type)
        assertEquals(text, minor, p.amountMinor)
        assertEquals(text, currency, p.currency)
        assertEquals(text, payee, p.payee)
        assertEquals(text, account, p.accountHint)
        assertEquals(text, reference, p.reference)
        assertEquals(text, balance, p.balanceMinor)
    }

    @Test
    fun hdfcUpi() =
        check(
            "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 13/11/26 Ref 512345678901 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808",
            TxType.EXPENSE,
            250_00,
            "Swiggy",
            "1234",
            "512345678901",
        )

    @Test
    fun hdfcCard() =
        check(
            "Spent Rs.1,499 On HDFC Bank Card 5678 At AMAZON On 2026-11-12:20:15:01.Not You? To Block+Reissue Call 18002586161",
            TxType.EXPENSE,
            1_499_00,
            "Amazon",
            "5678",
        )

    @Test
    fun sbiUpi() =
        check(
            "Dear UPI user A/C X1234 debited by 250.0 on date 13Nov26 trf to SWIGGY Refno 512345678901. If not u? call 1800111109. -SBI",
            TxType.EXPENSE,
            250_00,
            "Swiggy",
            "1234",
            "512345678901",
        )

    @Test
    fun iciciUpi() =
        check(
            "ICICI Bank Acct XX123 debited for Rs 450.00 on 12-Nov-26; ZOMATO credited. UPI:512345678902. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
            TxType.EXPENSE,
            450_00,
            "Zomato",
            "123",
            "512345678902",
        )

    @Test
    fun axisSalaryWithBalance() =
        check(
            "INR 50000.00 credited to A/c no. XX1234 on 01-11-26 at 10:15:01 IST. Info- SALARY NOV. Avl Bal- INR 152340.50 - Axis Bank",
            TxType.INCOME,
            50_000_00,
            "Salary Nov",
            "1234",
            balance = 152_340_50,
        )

    @Test
    fun kotakVpa() =
        check(
            "Rs.120.00 debited from a/c XX4321 on 12-11-26 to VPA uber@ybl (UPI Ref no 512345678903). Not you, https://kotak.com/fraud",
            TxType.EXPENSE,
            120_00,
            "Uber",
            "4321",
            "512345678903",
        )

    @Test
    fun balanceBeforeAmountIsNotTheAmount() =
        check(
            "Avl Bal: Rs.12,345.67. Your a/c XX9876 is debited for Rs.300.00 towards ATM withdrawal.",
            TxType.EXPENSE,
            300_00,
            "",
            "9876",
            balance = 12_345_67,
        )

    @Test fun gpayNotification() = check("₹250 paid to Swiggy", TxType.EXPENSE, 250_00, "Swiggy")

    @Test
    fun phonePeReceived() =
        check("Received ₹500 from Ravi Kumar", TxType.INCOME, 500_00, "Ravi Kumar")

    @Test
    fun paytm() = check("Paid Rs.99 to Netflix using Paytm UPI", TxType.EXPENSE, 99_00, "Netflix")

    @Test
    fun dollars() =
        check(
            "You spent \$12.50 at Starbucks on your card ending 4242",
            TxType.EXPENSE,
            12_50,
            "Starbucks",
            "4242",
            currency = "USD",
        )

    @Test
    fun refundIsIncome() =
        check(
            "Refund of Rs.349.00 credited to your a/c XX1234 from FLIPKART",
            TxType.INCOME,
            349_00,
            "Flipkart",
            "1234",
        )

    @Test
    fun ignoresWhatIsNotAPayment() {
        listOf(
                "123456 is your OTP for transaction of Rs.2000 at AMAZON. Do not share it with anyone.",
                "Get a pre-approved loan of Rs 5,00,000 at 10.5%. Apply now!",
                "Rs.649 will be debited from your a/c on 15-11-26 for NETFLIX mandate",
                "Ravi has requested Rs.500 from you on Google Pay",
                "Your HDFC Card statement is generated. Total amount due Rs.12,000. Minimum due Rs.600",
                "Hi, are we meeting at 5?",
                "Transaction of Rs.500 at XYZ failed",
            )
            .forEach { assertNull(it, PaymentParser.parse(it)) }
    }

    @Test
    fun recognisesBankSenders() {
        assertEquals(true, Sources.isBankSender("AX-HDFCBK"))
        assertEquals(true, Sources.isBankSender("VM-SBIUPI-S"))
        assertEquals(false, Sources.isBankSender("+919876543210"))
        assertEquals(false, Sources.isBankSender("9876543210"))
    }
}
