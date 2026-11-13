// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.AccountType
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxType

class DataTest {
    @Test
    fun backupRoundTripsAndRefusesTheWrongPassphrase() {
        val snapshot =
            Snapshot(
                accounts =
                    listOf(
                        Account(id = "a", name = "Bank", type = AccountType.BANK, currency = "INR")
                    ),
                transactions =
                    listOf(
                        Transaction(
                            type = TxType.EXPENSE,
                            occurredAt = 1,
                            accountId = "a",
                            amountMinor = 250_00,
                            currency = "INR",
                            payee = "Swiggy",
                        )
                    ),
            )
        val bytes =
            BackupCrypto.encrypt(
                snapshot.toJson(),
                "correct horse".toCharArray(),
                iterations = 1_000,
            )
        assertEquals(
            snapshot,
            Snapshot.fromJson(BackupCrypto.decrypt(bytes, "correct horse".toCharArray())),
        )
        assertThrows(WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(bytes, "wrong".toCharArray())
        }
    }

    @Test
    fun csvRoundTripsOurExport() {
        val bank = Account(id = "a", name = "Bank, main", type = AccountType.BANK, currency = "INR")
        val tx =
            Transaction(
                type = TxType.EXPENSE,
                occurredAt =
                    LocalDate.parse("2026-11-02")
                        .atTime(12, 0)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
                accountId = "a",
                amountMinor = 250_50,
                currency = "INR",
                payee = "Say \"hi\"",
            )
        val rows = Csv.parse(Csv.export(listOf(tx), listOf(bank), emptyList(), ZoneOffset.UTC))
        assertEquals(1, rows.size)
        assertEquals("Bank, main", rows[0].account)
        assertEquals("Say \"hi\"", rows[0].payee)
        assertEquals(TxType.EXPENSE, rows[0].type)
        assertEquals(LocalDate.parse("2026-11-02"), rows[0].date)
    }

    @Test
    fun csvReadsBankStatements() {
        val text =
            "Date;Narration;Withdrawal Amt.;Deposit Amt.\n02/11/2026;UPI-SWIGGY;250.00;\n03/11/2026;SALARY;;50,000.00\n"
        val rows = Csv.parse(text)
        assertEquals(2, rows.size)
        assertEquals("-250.00", rows[0].amount.toPlainString())
        assertEquals("50000.00", rows[1].amount.toPlainString())
        assertEquals(LocalDate.parse("2026-11-03"), rows[1].date)
    }
}
