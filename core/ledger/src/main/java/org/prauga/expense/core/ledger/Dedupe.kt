// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.ledger

import kotlin.math.abs
import org.prauga.expense.core.model.Transaction

private const val WINDOW_MILLIS = 15 * 60 * 1000L

/**
 * Whether [a] and [b] describe the same payment seen twice, e.g. a UPI app's notification and the
 * bank's SMS. Same amount, currency and direction within 15 minutes, and nothing that contradicts
 * it (a different UPI reference or a different card or account number).
 */
fun sameMoneyMovement(a: Transaction, b: Transaction): Boolean {
    if (a.amountMinor != b.amountMinor || a.currency != b.currency || a.type != b.type) return false
    if (a.reference != null && a.reference == b.reference) return true
    if (abs(a.occurredAt - b.occurredAt) > WINDOW_MILLIS) return false
    if (a.reference != null && b.reference != null) return false
    if (a.accountHint != null && b.accountHint != null && a.accountHint != b.accountHint)
        return false
    return true
}

/** Merges the details of [extra] into [kept]: fills a missing payee, reference or account hint. */
fun merge(kept: Transaction, extra: Transaction): Transaction =
    kept.copy(
        payee = kept.payee.ifBlank { extra.payee },
        reference = kept.reference ?: extra.reference,
        accountHint = kept.accountHint ?: extra.accountHint,
        categoryId = kept.categoryId ?: extra.categoryId,
        raw = listOfNotNull(kept.raw, extra.raw).joinToString("\n---\n").ifBlank { null },
    )
