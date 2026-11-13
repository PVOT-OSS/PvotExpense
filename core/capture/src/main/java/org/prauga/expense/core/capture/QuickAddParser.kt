// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import org.prauga.expense.core.model.Money
import org.prauga.expense.core.model.TxType

data class QuickEntry(
    val amountMinor: Long?,
    val currency: String,
    val type: TxType,
    val payee: String,
    val categoryHint: String?,
    val accountHint: String?,
    val toAccountHint: String?,
    val date: LocalDate,
)

/**
 * Reads one line like "250 lunch with ravi #food @hdfc yesterday", "+50k salary", "$12.50 coffee"
 * or "2000 @hdfc > @cash" (a transfer).
 */
object QuickAddParser {
    private val AMOUNT =
        Regex(
            """^([+\-])?$CURRENCY?([0-9][0-9,]*(?:\.[0-9]+)?)(k|l|lakh|cr)?$CURRENCY?$""",
            RegexOption.IGNORE_CASE,
        )
    private val CODE =
        Regex("""^(inr|usd|eur|gbp|aed|sgd|jpy|rs|₹|\$|€|£)$""", RegexOption.IGNORE_CASE)
    private val INCOME_WORDS =
        setOf(
            "salary",
            "received",
            "refund",
            "income",
            "got",
            "bonus",
            "interest",
            "dividend",
            "cashback",
        )
    private val MONTHS = Month.entries.associateBy { it.name.take(3).lowercase(Locale.ROOT) }
    private val DAYS = DayOfWeek.entries.associateBy { it.name.take(3).lowercase(Locale.ROOT) }
    private val SLASH_DATE = Regex("""^(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?$""")

    fun parse(text: String, today: LocalDate, defaultCurrency: String): QuickEntry {
        val words = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.toMutableList()
        var amount: BigDecimal? = null
        var currency: String? = null
        var sign: String? = null
        var category: String? = null
        val accounts = mutableListOf<String>()
        var transfer = false
        var date = today
        val rest = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val lower = w.lowercase(Locale.ROOT)
            val m = AMOUNT.matchEntire(w)
            when {
                amount == null && m != null -> {
                    sign = m.groupValues[1].ifEmpty { null }
                    currency =
                        (m.groupValues[2].ifEmpty { m.groupValues[5] })
                            .ifEmpty { null }
                            ?.let { currencyCode(it, defaultCurrency) }
                    val base = m.groupValues[3].replace(",", "").toBigDecimal()
                    amount =
                        when (m.groupValues[4].lowercase(Locale.ROOT)) {
                            "k" -> base * BigDecimal(1_000)
                            "l",
                            "lakh" -> base * BigDecimal(100_000)
                            "cr" -> base * BigDecimal(10_000_000)
                            else -> base
                        }
                    if (currency == null && i + 1 < words.size && CODE.matches(words[i + 1])) {
                        currency = currencyCode(words[i + 1], defaultCurrency)
                        i++
                    }
                }
                amount == null &&
                    CODE.matches(w) &&
                    i + 1 < words.size &&
                    AMOUNT.matches(words[i + 1]) -> currency = currencyCode(w, defaultCurrency)
                w.startsWith("#") && w.length > 1 -> category = w.drop(1)
                w.startsWith("@") && w.length > 1 -> accounts += w.drop(1)
                w == ">" || lower == "to" && i + 1 < words.size && words[i + 1].startsWith("@") ->
                    transfer = true
                lower == "today" -> date = today
                lower == "yesterday" -> date = today.minusDays(1)
                DAYS.containsKey(lower.take(3)) &&
                    (lower.length == 3 ||
                        DayOfWeek.entries.any { it.name.lowercase(Locale.ROOT) == lower }) ->
                    date =
                        today.with(TemporalAdjusters.previousOrSame(DAYS.getValue(lower.take(3))))
                SLASH_DATE.matches(w) -> date = slashDate(w, today) ?: date.also { rest += w }
                lower.toIntOrNull()?.let { it in 1..31 } == true &&
                    i + 1 < words.size &&
                    MONTHS.containsKey(words[i + 1].lowercase(Locale.ROOT).take(3)) -> {
                    date =
                        pastDate(
                            MONTHS.getValue(words[i + 1].lowercase(Locale.ROOT).take(3)),
                            lower.toInt(),
                            today,
                        )
                    i++
                }
                else -> rest += w
            }
            i++
        }
        val cur = currency ?: defaultCurrency
        val type =
            when {
                transfer && accounts.size >= 2 -> TxType.TRANSFER
                sign == "+" -> TxType.INCOME
                rest.any { it.lowercase(Locale.ROOT) in INCOME_WORDS } -> TxType.INCOME
                else -> TxType.EXPENSE
            }
        return QuickEntry(
            amountMinor = amount?.let { Money.of(it, cur).minor },
            currency = cur,
            type = type,
            payee = rest.joinToString(" ").replaceFirstChar { it.titlecase(Locale.ROOT) },
            categoryHint = category,
            accountHint = accounts.firstOrNull(),
            toAccountHint = if (type == TxType.TRANSFER) accounts[1] else null,
            date = date,
        )
    }

    private fun slashDate(w: String, today: LocalDate): LocalDate? {
        val (d, m, y) = SLASH_DATE.matchEntire(w)!!.destructured
        val month = m.toInt().takeIf { it in 1..12 } ?: return null
        val day = d.toInt()
        if (y.isEmpty()) return runCatching { pastDate(Month.of(month), day, today) }.getOrNull()
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun pastDate(month: Month, day: Int, today: LocalDate): LocalDate {
        val d = LocalDate.of(today.year, month, day.coerceAtMost(month.length(today.isLeapYear)))
        return if (d.isAfter(today)) d.minusYears(1) else d
    }
}
