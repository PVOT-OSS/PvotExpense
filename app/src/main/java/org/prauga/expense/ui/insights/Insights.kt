// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.progress.PvotProportionBar
import com.prauga.pvot.designsystem.components.progress.PvotProportionBarColors
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.format.DateTimeFormatter
import org.prauga.expense.R
import org.prauga.expense.core.ledger.Period
import org.prauga.expense.core.ledger.categoryInsights
import org.prauga.expense.core.ledger.monthlyTotals
import org.prauga.expense.core.ledger.topPayees
import org.prauga.expense.core.ledger.within
import org.prauga.expense.core.model.TxStatus
import org.prauga.expense.core.model.TxType
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.Heading
import org.prauga.expense.ui.money

/** How many periods back from the current one the screen looks. */
data class InsightsState(val back: Int = 0)

@Composable
private fun palette(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.outline,
    )

fun LazyListScope.insightsContent(
    state: AppState,
    insights: InsightsState,
    onChange: (InsightsState) -> Unit,
) {
    val start = state.settings.monthStartDay
    var period: Period = state.period
    repeat(insights.back) { period = period.previous(start) }
    val confirmed = state.data.transactions.filter { it.status == TxStatus.CONFIRMED }
    val inPeriod = confirmed.within(period, state.zone)
    val cats = categoryInsights(confirmed, period, start, state.zone, state.fx)
    val spent = cats.sumOf { it.spentMinor }
    val income =
        inPeriod
            .filter { it.type == TxType.INCOME }
            .sumOf { state.fx.toBase(it.amountMinor, it.currency) }

    item("period") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PvotIconButton(
                painterResource(R.drawable.ic_chevron_left),
                stringResource(R.string.back),
                onClick = { onChange(insights.copy(back = insights.back + 1)) },
            )
            val f = DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0])
            Text(
                if (insights.back == 0) stringResource(R.string.this_month)
                else "${period.start.format(f)} – ${period.endExclusive.minusDays(1).format(f)}",
                Modifier.weight(1f).padding(horizontal = PvotSpacing.S),
                style = MaterialTheme.typography.titleMedium,
            )
            if (insights.back > 0)
                PvotIconButton(
                    painterResource(R.drawable.ic_chevron_right),
                    stringResource(R.string.this_month),
                    onClick = { onChange(insights.copy(back = insights.back - 1)) },
                )
        }
    }
    item("total") {
        PvotCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(PvotSpacing.L),
                verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
            ) {
                Text(money(spent, state.base), style = MaterialTheme.typography.displaySmall)
                Caption(
                    stringResource(
                        R.string.income_vs_spend,
                        money(income, state.base),
                        money(spent, state.base),
                    ),
                    Modifier.padding(start = 0.dp),
                )
                if (spent > 0) {
                    val top = cats.take(5)
                    val colors = palette()
                    PvotProportionBar(
                        segments = top.map { it.spentMinor.toFloat() / spent },
                        colors =
                            PvotProportionBarColors(
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                segmentColors = colors,
                            ),
                    )
                }
            }
        }
    }
    if (spent == 0L) {
        item("empty") { Caption(stringResource(R.string.empty_insights)) }
        return
    }
    cats
        .firstOrNull { it.unusual }
        ?.let { u ->
            item("unusual") {
                PvotCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            R.string.unusual,
                            state.data.category(u.categoryId)?.name
                                ?: stringResource(R.string.no_category),
                        ),
                        Modifier.padding(PvotSpacing.L),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    item("cat-h") { Heading(stringResource(R.string.by_category)) }
    item("cats") {
        val colors = palette()
        PvotListGroup {
            cats
                .filter { it.spentMinor > 0 }
                .forEachIndexed { i, c ->
                    item {
                        PvotListRow(
                            title =
                                state.data.category(c.categoryId)?.name
                                    ?: stringResource(R.string.no_category),
                            supportingText =
                                if (c.averageMinor > 0)
                                    stringResource(
                                        R.string.vs_average,
                                        money(c.averageMinor, state.base),
                                    )
                                else null,
                            leading = {
                                Box(
                                    Modifier.size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i < 5) colors[i]
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                )
                            },
                            trailing = {
                                Text(
                                    money(c.spentMinor, state.base),
                                    style = MaterialTheme.typography.titleSmall,
                                    color =
                                        if (c.unusual) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                        )
                    }
                }
        }
    }
    item("trend-h") { Heading(stringResource(R.string.trend)) }
    item("trend") { Trend(monthlyTotals(confirmed, period, start, state.zone, state.fx), state) }
    val payees = topPayees(inPeriod, state.fx)
    if (payees.isNotEmpty()) {
        item("payees-h") { Heading(stringResource(R.string.top_payees)) }
        item("payees") {
            PvotListGroup {
                payees.forEach { (name, minor) ->
                    item {
                        PvotListRow(
                            title = name,
                            trailing = {
                                Text(
                                    money(minor, state.base),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Trend(totals: List<Pair<Period, Long>>, state: AppState) {
    val max = totals.maxOf { it.second }.coerceAtLeast(1)
    val f = DateTimeFormatter.ofPattern("MMM", LocalConfiguration.current.locales[0])
    PvotCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(160.dp).padding(PvotSpacing.L),
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
            verticalAlignment = Alignment.Bottom,
        ) {
            totals.forEachIndexed { i, (p, v) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier.fillMaxWidth()
                                .fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 1f))
                                .clip(PvotShapes.Small)
                                .background(
                                    if (i == totals.lastIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                        )
                    }
                    Spacer(Modifier.height(PvotSpacing.XS))
                    Text(
                        p.start.plusDays(15).format(f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Caption(
            money(totals.last().second, state.base),
            Modifier.padding(start = PvotSpacing.L, bottom = PvotSpacing.L),
        )
    }
}
