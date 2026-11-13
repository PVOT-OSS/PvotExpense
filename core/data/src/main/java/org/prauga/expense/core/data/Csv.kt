// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.Money
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxType

data class ImportRow(
    val date: LocalDate,
    val amount: BigDecimal,
    val currency: String?,
    val payee: String,
    val category: String?,
    val account: String?,
    val note: String,
    val type: TxType?,
)

object Csv {
    private val HEADER =
        listOf(
            "date",
            "type",
            "amount",
            "currency",
            "account",
            "to_account",
            "category",
            "payee",
            "note",
            "status",
        )

    fun export(
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
        zone: ZoneId,
    ): String {
        val acc = accounts.associate { it.id to it.name }
        val cat = categories.associate { it.id to it.name }
        val rows =
            transactions
                .filter { !it.deleted }
                .sortedBy { it.occurredAt }
                .map { t ->
                    listOf(
                        Instant.ofEpochMilli(t.occurredAt)
                            .atZone(zone)
                            .toLocalDateTime()
                            .toString(),
                        t.type.name.lowercase(Locale.ROOT),
                        Money(t.amountMinor, t.currency).decimal.toPlainString(),
                        t.currency,
                        acc[t.accountId].orEmpty(),
                        t.toAccountId?.let { acc[it] }.orEmpty(),
                        t.categoryId?.let { cat[it] }.orEmpty(),
                        t.payee,
                        t.note,
                        t.status.name.lowercase(Locale.ROOT),
                    )
                }
        return (listOf(HEADER) + rows).joinToString("\n") { row ->
            row.joinToString(",", transform = ::quote)
        } + "\n"
    }

    private fun quote(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\""
        else v

    fun parseLines(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val delimiter =
            if (
                text.lineSequence().firstOrNull().orEmpty().count { it == ';' } >
                    text.lineSequence().firstOrNull().orEmpty().count { it == ',' }
            )
                ';'
            else ','
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> {
                    row += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    private val DATE_FORMATS =
        listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "dd.MM.yyyy",
                "dd/MM/yy",
                "d MMM yyyy",
                "dd MMM yyyy",
                "MM/dd/yyyy",
            )
            .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private fun date(text: String): LocalDate? {
        val t = text.trim()
        for (c in listOf(t, t.substringBefore('T'), t.substringBefore(' '))) {
            for (f in DATE_FORMATS) runCatching {
                return LocalDate.parse(c, f)
            }
        }
        return null
    }

    private fun column(header: List<String>, vararg names: String): Int =
        header.indexOfFirst { h ->
            names.any { h.trim().lowercase(Locale.ROOT).replace(' ', '_') == it }
        }

    /**
     * Reads a CSV with a header row. Recognises our own export and the usual columns of other apps
     * and bank statements: date, amount (signed) or debit/credit, payee/description, category,
     * account, note.
     */
    fun parse(text: String): List<ImportRow> {
        val lines = parseLines(text.removePrefix("﻿"))
        if (lines.size < 2) return emptyList()
        val h = lines.first()
        val dateCol = column(h, "date", "transaction_date", "txn_date", "value_date", "time")
        val amountCol = column(h, "amount", "value", "sum")
        val debitCol =
            column(
                h,
                "debit",
                "withdrawal",
                "withdrawal_amt.",
                "withdrawal_amount",
                "expense",
                "out",
            )
        val creditCol =
            column(h, "credit", "deposit", "deposit_amt.", "deposit_amount", "income", "in")
        val payeeCol =
            column(
                h,
                "payee",
                "description",
                "narration",
                "merchant",
                "name",
                "title",
                "details",
                "particulars",
            )
        val categoryCol = column(h, "category")
        val accountCol = column(h, "account", "wallet")
        val noteCol = column(h, "note", "notes", "memo", "comment")
        val currencyCol = column(h, "currency")
        val typeCol = column(h, "type")
        if (dateCol < 0 || (amountCol < 0 && debitCol < 0 && creditCol < 0)) return emptyList()
        return lines.drop(1).mapNotNull { r ->
            fun at(i: Int) = if (i in r.indices) r[i].trim() else ""
            fun number(s: String) = s.replace(Regex("""[^0-9.\-]"""), "").toBigDecimalOrNull()
            val d = date(at(dateCol)) ?: return@mapNotNull null
            val type =
                at(typeCol).lowercase(Locale.ROOT).let { t ->
                    TxType.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == t }
                }
            val amount =
                when {
                    amountCol >= 0 -> number(at(amountCol))
                    else -> {
                        val debit = number(at(debitCol))?.takeIf { it.signum() != 0 }
                        val credit = number(at(creditCol))?.takeIf { it.signum() != 0 }
                        debit?.abs()?.negate() ?: credit
                    }
                } ?: return@mapNotNull null
            if (amount.signum() == 0) return@mapNotNull null
            ImportRow(
                d,
                amount,
                at(currencyCol).ifEmpty { null },
                at(payeeCol),
                at(categoryCol).ifEmpty { null },
                at(accountCol).ifEmpty { null },
                at(noteCol),
                type,
            )
        }
    }
}
