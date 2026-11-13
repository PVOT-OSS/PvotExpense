// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.capture

import org.prauga.expense.core.model.TxType

/** A payment read out of a bank SMS or a payment app's notification. */
data class CapturedPayment(
    val type: TxType,
    val amountMinor: Long,
    val currency: String,
    val payee: String,
    val accountHint: String?,
    val reference: String?,
    val balanceMinor: Long?,
)

/**
 * Reads payments out of the free text banks and UPI apps send. It only accepts text that states a
 * completed debit or credit with an amount; OTPs, offers, requests and future debits are refused.
 */
object PaymentParser {
    private val OPT = setOf(RegexOption.IGNORE_CASE)

    private val IGNORE =
        Regex(
            """\botp\b|one.time.password|verification code|will be (debited|charged|deducted)|""" +
                """has requested|requested (money|rs|inr|₹)|request(ed)? of|pre.?approved|loan of|apply now|""" +
                """minimum (amount )?due|total (amount )?due|statement (is )?generated|e.?mandate.*(created|registered)|""" +
                """\boffer\b|cashback of up|\bwin\b|failed|declined|reversed?\b.*will|is due""",
            OPT,
        )
    private val DEBIT =
        Regex(
            """\b(debited|spent|paid|sent|withdrawn|withdrawal|purchase|deducted|txn of|transaction of|transferred to|payment of)\b""",
            OPT,
        )
    private val CREDIT =
        Regex("""\b(credited|received|deposited|refund(ed)?|added to|reversal)\b""", OPT)
    private val BALANCE =
        Regex(
            """(avl\.?|avbl\.?|available|clear|closing|a/c)?\s*\bbal(ance)?\.?\s*(is)?\s*[:\-]?\s*""" +
                CURRENCY +
                """?\s*[:\-]?\s*""" +
                NUMBER,
            OPT,
        )
    private val AMOUNT = Regex(CURRENCY + """\s*[:\-]?\s*""" + NUMBER, OPT)
    private val BARE_AMOUNT =
        Regex("""(?:debited|credited|deducted)\s+(?:by|for|with)\s+""" + NUMBER, OPT)
    private val ACCOUNT =
        Regex(
            """\b(?:a/c|ac|acct|account|card|a/c no)\.?\s*(?:no\.?|number)?\s*(?:ending\s*(?:with|in)?)?\s*[:\-]?\s*[x*.\s]*(\d{3,4})\b""",
            OPT,
        )
    private val REFERENCE =
        Regex(
            """(?:upi\s*ref(?:erence)?\.?\s*(?:no|id|number)?|ref(?:erence)?\.?\s*(?:no|id|number)?|refno|rrn|utr|upi|txn\s*id|transaction\s*id)\s*[:.#\-]*\s*(\d{9,18})""",
            OPT,
        )
    private val TAIL =
        Regex(
            """\b(not you|not u\b|if not|not done by you|call \d|sms block|to block|for dispute|https?://|www\.)""",
            OPT,
        )

    private val PAYEE_PATTERNS =
        listOf(
            Regex("""\bvpa\s+([a-z0-9._\-]+)@[a-z0-9.]+""", OPT),
            Regex("""\btrf to\s+(.+?)(?=\s+ref|\s+upi|\.|$)""", OPT),
            Regex(""";\s*([^;]+?)\s+credited""", OPT),
            Regex(
                """\b(?:paid|sent|payment)\s+(?:of\s+)?(?:$CURRENCY\s*$NUMBER\s+)?to\s+(.+?)(?=\s+on\b|\s+via\b|\s+ref|\s+upi|\s+using|\s+from|[.,(]|$)""",
                OPT,
            ),
            Regex(
                """\bat\s+([a-z0-9][a-z0-9 &._'*\-]{1,40}?)(?=\s+on\b|\s+via\b|\s+ref|\s+\d|[.,(]|$)""",
                OPT,
            ),
            Regex(
                """\bto\s+([a-z0-9][a-z0-9 &._'@*\-]{1,40}?)(?=\s+on\b|\s+via\b|\s+ref|\s+upi|\s+using|[.,(]|$)""",
                OPT,
            ),
            Regex(
                """\bfrom\s+([a-z][a-z0-9 &._'@\-]{1,40}?)(?=\s+on\b|\s+via\b|\s+ref|\s+upi|\s+to\b|[.,(]|$)""",
                OPT,
            ),
            Regex("""\binfo\s*[:\-]\s*(.+?)(?=\s*\.|\s+avl|$)""", OPT),
        )
    private val NOT_A_PAYEE =
        Regex("""\b(a/c|acct|account|card|bank a/c|your|you|self)\b|^x+\d+$""", OPT)

    fun parse(text: String, fallbackCurrency: String = "INR"): CapturedPayment? {
        val clean = text.replace(Regex("""\s+"""), " ").trim()
        if (clean.isEmpty() || IGNORE.containsMatchIn(clean)) return null
        val debit = DEBIT.find(clean)?.range?.first
        val credit = CREDIT.find(clean)?.range?.first
        val type =
            when {
                debit == null && credit == null -> return null
                credit == null -> TxType.EXPENSE
                debit == null -> TxType.INCOME
                debit <= credit -> TxType.EXPENSE
                else -> TxType.INCOME
            }
        val balanceMatch = BALANCE.find(clean)
        val withoutBalance = balanceMatch?.let { clean.removeRange(it.range) } ?: clean
        val amount =
            AMOUNT.find(withoutBalance)?.let {
                currencyCode(it.groupValues[1], fallbackCurrency) to it.groupValues[2]
            }
                ?: BARE_AMOUNT.find(withoutBalance)?.let { fallbackCurrency to it.groupValues[1] }
                ?: return null
        val minor = amountMinor(amount.second, amount.first) ?: return null
        val balance =
            balanceMatch?.let {
                amountMinor(
                    it.groupValues[5],
                    currencyCode(it.groupValues[4].ifEmpty { null }, amount.first),
                )
            }
        val head =
            TAIL.find(withoutBalance)?.let { withoutBalance.substring(0, it.range.first) }
                ?: withoutBalance
        return CapturedPayment(
            type = type,
            amountMinor = minor,
            currency = amount.first,
            payee = payee(head),
            accountHint = ACCOUNT.find(clean)?.groupValues?.get(1),
            reference = REFERENCE.find(clean)?.groupValues?.get(1),
            balanceMinor = balance,
        )
    }

    private fun payee(text: String): String {
        for (pattern in PAYEE_PATTERNS) {
            for (m in pattern.findAll(text)) {
                val candidate =
                    m.groupValues.last { it.isNotEmpty() }.trim().trim('.', ',', '-', ':', '*')
                if (candidate.length < 2 || NOT_A_PAYEE.containsMatchIn(candidate)) continue
                if (candidate.any { it.isDigit() } && candidate.count { it.isLetter() } < 2)
                    continue
                return Payees.display(candidate)
            }
        }
        return ""
    }
}
