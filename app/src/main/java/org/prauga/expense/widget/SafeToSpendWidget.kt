// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import org.prauga.expense.LedgerApplication
import org.prauga.expense.MainActivity
import org.prauga.expense.R
import org.prauga.expense.core.ledger.safeToSpend
import org.prauga.expense.ui.Formats

class SafeToSpendWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as LedgerApplication).container
        val data = container.repository.data.first()
        val settings = container.settings.now()
        val zone = ZoneId.systemDefault()
        val safe =
            safeToSpend(
                data.accounts,
                data.confirmed,
                data.schedules,
                data.budgets,
                LocalDate.now(zone),
                settings.monthStartDay,
                zone,
                data.converter(settings.baseCurrency),
            )
        val amount =
            if (settings.hideAmounts || settings.lock) context.getString(R.string.hidden_amount)
            else Formats.money(safe.perDayMinor, settings.baseCurrency)
        val open = Intent(context, MainActivity::class.java)
        val add = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_QUICK_ADD)
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(24.dp)
                        .padding(16.dp)
                        .clickable(actionStartActivity(open))
                ) {
                    Row(
                        GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(
                                context.getString(R.string.widget_label),
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    ),
                            )
                            Text(
                                amount,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                maxLines = 1,
                            )
                            Text(
                                context.getString(R.string.widget_per_day),
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    ),
                            )
                        }
                        Spacer(GlanceModifier.size(8.dp))
                        Box(
                            GlanceModifier.size(48.dp)
                                .background(GlanceTheme.colors.primary)
                                .cornerRadius(24.dp)
                                .clickable(actionStartActivity(add)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                ImageProvider(R.drawable.ic_plus),
                                context.getString(R.string.tile_label),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { SafeToSpendWidget().updateAll(context) }
        }
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}
