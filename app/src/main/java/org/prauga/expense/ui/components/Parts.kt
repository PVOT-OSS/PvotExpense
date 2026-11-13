// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotLetterTile
import com.prauga.pvot.designsystem.components.list.PvotListRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.prauga.expense.R
import org.prauga.expense.core.data.LedgerData
import org.prauga.expense.core.model.AccountType
import org.prauga.expense.core.model.Transaction
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.money

@DrawableRes
fun AccountType.icon(): Int =
    when (this) {
        AccountType.CASH -> R.drawable.ic_cash
        AccountType.BANK -> R.drawable.ic_bank
        AccountType.WALLET -> R.drawable.ic_wallet
        AccountType.CARD -> R.drawable.ic_card
        AccountType.LOAN -> R.drawable.ic_loan
        AccountType.PERSON -> R.drawable.ic_user
    }

fun AccountType.label(): Int =
    when (this) {
        AccountType.CASH -> R.string.type_cash
        AccountType.BANK -> R.string.type_bank
        AccountType.WALLET -> R.string.type_wallet
        AccountType.CARD -> R.string.type_card
        AccountType.LOAN -> R.string.type_loan
        AccountType.PERSON -> R.string.type_person
    }

@Composable
fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier.size(40.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun AmountText(tx: Transaction, modifier: Modifier = Modifier) {
    val color =
        when (tx.type) {
            TxType.INCOME -> MaterialTheme.colorScheme.primary
            TxType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
            TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        }
    val minor = if (tx.type == TxType.EXPENSE) -tx.amountMinor else tx.amountMinor
    Text(
        money(minor, tx.currency, signed = tx.type == TxType.INCOME),
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        maxLines = 1,
    )
}

/** A transaction as a list row: who, what category and account, and how much. */
@Composable
fun TxRow(
    tx: Transaction,
    data: LedgerData,
    zone: ZoneId,
    onClick: () -> Unit,
    showDate: Boolean = false,
) {
    val category = data.category(tx.categoryId)
    val account = data.account(tx.accountId)
    val title =
        when {
            tx.payee.isNotBlank() -> tx.payee
            tx.type == TxType.TRANSFER -> stringResource(R.string.transfer)
            category != null -> category.name
            else -> stringResource(R.string.no_category)
        }
    val details =
        buildList {
                if (tx.type == TxType.TRANSFER) {
                    add(
                        "${account?.name.orEmpty()} → ${data.account(tx.toAccountId)?.name.orEmpty()}"
                    )
                } else {
                    add(category?.name ?: stringResource(R.string.no_category))
                    account?.let { add(it.name) }
                }
                if (showDate)
                    add(
                        Formats.shortDate(
                            Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate()
                        )
                    )
                else add(Formats.time(tx.occurredAt, zone))
            }
            .joinToString(" · ")
    PvotListRow(
        title = title,
        supportingText = details,
        leading = {
            when {
                tx.type == TxType.TRANSFER -> IconTile(R.drawable.ic_transfer)
                tx.status == TxStatus.PENDING ->
                    IconTile(
                        R.drawable.ic_inbox,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                else -> PvotLetterTile(category?.name ?: title, colorKey = category?.id ?: title)
            }
        },
        trailing = { AmountText(tx) },
        onClick = onClick,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) =
    CardCaption(text, modifier.padding(horizontal = 4.dp))

/** A caption inside a card, aligned with the card's other text. */
@Composable
fun CardCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

data class Choice<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    @DrawableRes val icon: Int? = null,
)

@Composable
fun <T> PickerSheet(
    visible: Boolean,
    title: String,
    choices: List<Choice<T>>,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    PvotBottomSheet(visible = visible, onDismissRequest = onDismiss, title = title) {
        Column(Modifier.padding(bottom = 16.dp)) {
            choices.forEach { c ->
                PvotSheetItem(
                    title = c.label,
                    subtitle = c.subtitle,
                    icon = c.icon?.let { { Icon(painterResource(it), contentDescription = null) } },
                    onClick = {
                        onPick(c.value)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** A field-like row that opens a picker. */
@Composable
fun PickerField(label: String, value: String, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    PvotListRow(
        title = value,
        supportingText = label,
        leading = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailing = { Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(
    visible: Boolean,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PvotButton(
                text = stringResource(R.string.done),
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
                style = PvotButtonStyle.Text,
            )
        },
        dismissButton = {
            PvotButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = PvotButtonStyle.Text,
            )
        },
    ) {
        DatePicker(state)
    }
}

@Composable
fun SingleLine(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
