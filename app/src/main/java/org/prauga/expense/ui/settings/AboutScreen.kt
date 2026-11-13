// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.prauga.expense.R
import org.prauga.expense.ui.components.Caption
import org.prauga.expense.ui.components.SubScreen

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    SubScreen(stringResource(R.string.about), onBack) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
        item { Caption(stringResource(R.string.version, version)) }
        item {
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyLarge)
        }
        item { Caption(stringResource(R.string.licences)) }
    }
}
