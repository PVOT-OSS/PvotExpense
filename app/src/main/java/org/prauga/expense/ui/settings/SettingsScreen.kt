// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import org.prauga.expense.R
import org.prauga.expense.capture.SmsSource
import org.prauga.expense.ui.AppState
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Formats
import org.prauga.expense.ui.Nav
import org.prauga.expense.ui.components.Choice
import org.prauga.expense.ui.components.PickerSheet
import org.prauga.expense.ui.components.SubScreen

val CURRENCIES =
    listOf(
        "INR",
        "USD",
        "EUR",
        "GBP",
        "AED",
        "SGD",
        "AUD",
        "CAD",
        "JPY",
        "CHF",
        "CNY",
        "SAR",
        "QAR",
        "KWD",
        "NZD",
        "ZAR",
        "BDT",
        "LKR",
        "NPR",
        "PKR",
        "MYR",
        "THB",
        "IDR",
        "PHP",
        "HKD",
        "KRW",
        "SEK",
        "NOK",
        "DKK",
        "BRL",
        "MXN",
    )

private fun granted(context: Context, permissions: Array<String>) =
    permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun listenerEnabled(context: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun Context.persist(uri: Uri, write: Boolean) {
    val flags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
}

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel, nav: Nav) {
    val context = LocalContext.current
    val s = state.settings
    var listener by remember { mutableStateOf(listenerEnabled(context)) }
    var smsGranted by remember { mutableStateOf(granted(context, SmsSource.PERMISSIONS)) }
    LifecycleResumeEffect(Unit) {
        listener = listenerEnabled(context)
        smsGranted = granted(context, SmsSource.PERMISSIONS)
        if (s.captureNotifications && !listener) vm.setCaptureNotifications(false)
        onPauseOrDispose {}
    }
    var disclosure by remember { mutableStateOf(false) }
    var picker by remember { mutableIntStateOf(0) }
    var passphraseFor by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val smsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            result ->
            smsGranted = result.values.all { it }
            if (smsGranted) {
                if (SmsSource.VIA_MESSAGES) vm.setCaptureMessages(true) else vm.setCaptureSms(true)
            }
        }
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                context.persist(it, write = true)
                vm.setBackupFolder(it.toString())
            }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                restoreUri = it.toString()
                confirmRestore = true
            }
        }
    val exportCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri
            ->
            uri?.let { vm.exportCsv(it.toString()) }
        }
    val importCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.importCsv(it.toString()) }
        }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    SubScreen(
        stringResource(R.string.settings),
        nav::back,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PvotListGroup(title = stringResource(R.string.general)) {
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.base_currency),
                        onClick = { picker = 1 },
                        value = s.baseCurrency,
                        summary = Formats.currencyName(s.baseCurrency),
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.month_start),
                        onClick = { picker = 2 },
                        value = stringResource(R.string.month_start_value, s.monthStartDay),
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.accounts),
                        onClick = nav::accounts,
                        icon = { Icon(painterResource(R.drawable.ic_wallet), null) },
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.categories),
                        onClick = nav::categories,
                        icon = { Icon(painterResource(R.drawable.ic_category), null) },
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.exchange_rates),
                        onClick = nav::rates,
                        icon = { Icon(painterResource(R.drawable.ic_coins), null) },
                    )
                }
            }
        }
        item {
            PvotListGroup(title = stringResource(R.string.capture)) {
                item {
                    PvotSwitchRow(
                        title = stringResource(R.string.capture_notifications),
                        summary = stringResource(R.string.capture_notifications_summary),
                        checked = s.captureNotifications && listener,
                        onCheckedChange = { on ->
                            if (!on) vm.setCaptureNotifications(false)
                            else if (listener)
                                vm.setCaptureNotifications(true).also { askNotifications() }
                            else disclosure = true
                        },
                        icon = { Icon(painterResource(R.drawable.ic_bell), null) },
                    )
                }
                if (SmsSource.VIA_MESSAGES) {
                    val available = SmsSource.available(context)
                    item {
                        PvotSwitchRow(
                            title = stringResource(R.string.capture_messages),
                            summary =
                                stringResource(
                                    if (available) R.string.capture_messages_summary
                                    else R.string.capture_messages_missing
                                ),
                            checked = s.captureMessages && smsGranted,
                            enabled = available,
                            onCheckedChange = { on ->
                                if (!on) vm.setCaptureMessages(false)
                                else if (smsGranted) vm.setCaptureMessages(true)
                                else smsPermission.launch(SmsSource.PERMISSIONS)
                            },
                            icon = { Icon(painterResource(R.drawable.ic_message), null) },
                        )
                    }
                } else {
                    item {
                        PvotSwitchRow(
                            title = stringResource(R.string.capture_sms),
                            summary = stringResource(R.string.capture_sms_summary),
                            checked = s.captureSms && smsGranted,
                            onCheckedChange = { on ->
                                if (!on) vm.setCaptureSms(false)
                                else if (smsGranted) vm.setCaptureSms(true)
                                else smsPermission.launch(SmsSource.PERMISSIONS)
                            },
                            icon = { Icon(painterResource(R.drawable.ic_message), null) },
                        )
                    }
                    if (s.captureSms && smsGranted)
                        item {
                            PvotPreferenceRow(
                                stringResource(R.string.import_sms),
                                onClick = vm::importSms,
                                summary = stringResource(R.string.import_sms_summary),
                            )
                        }
                }
            }
        }
        item {
            PvotListGroup(title = stringResource(R.string.privacy)) {
                item {
                    PvotSwitchRow(
                        stringResource(R.string.lock),
                        s.lock,
                        vm::setLock,
                        summary = stringResource(R.string.lock_summary),
                        icon = { Icon(painterResource(R.drawable.ic_lock), null) },
                    )
                }
                item {
                    PvotSwitchRow(
                        stringResource(R.string.hide_amounts),
                        s.hideAmounts,
                        vm::setHideAmounts,
                        summary = stringResource(R.string.hide_amounts_summary),
                        icon = { Icon(painterResource(R.drawable.ic_eye_off), null) },
                    )
                }
                item {
                    PvotSwitchRow(
                        stringResource(R.string.secure_screen),
                        s.secureScreen,
                        vm::setSecureScreen,
                        summary = stringResource(R.string.secure_screen_summary),
                        icon = { Icon(painterResource(R.drawable.ic_shield), null) },
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.encrypted),
                        onClick = {},
                        summary = stringResource(R.string.encrypted_summary),
                        showChevron = false,
                    )
                }
            }
        }
        item {
            PvotListGroup(title = stringResource(R.string.backup)) {
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.backup_folder),
                        onClick = { folder.launch(null) },
                        summary =
                            s.backupUri?.let {
                                Uri.decode(it).substringAfterLast(':').ifEmpty { it }
                            } ?: stringResource(R.string.backup_folder_none),
                        icon = { Icon(painterResource(R.drawable.ic_backup), null) },
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.backup_passphrase),
                        onClick = { passphraseFor = "" },
                        summary = stringResource(R.string.backup_passphrase_summary),
                        value =
                            if (s.hasPassphrase) stringResource(R.string.backup_passphrase_set)
                            else null,
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.backup_now),
                        onClick = vm::backupNow,
                        enabled = s.backupUri != null && s.hasPassphrase,
                        summary =
                            if (s.lastBackupAt > 0)
                                stringResource(
                                    R.string.backup_last,
                                    Formats.relative(s.lastBackupAt),
                                )
                            else stringResource(R.string.backup_never),
                        showChevron = false,
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.restore),
                        onClick = { restore.launch(arrayOf("*/*")) },
                        icon = { Icon(painterResource(R.drawable.ic_restore), null) },
                    )
                }
            }
        }
        item {
            PvotListGroup(title = stringResource(R.string.data)) {
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.export_csv),
                        onClick = { exportCsv.launch("ledger.csv") },
                        icon = { Icon(painterResource(R.drawable.ic_download), null) },
                    )
                }
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.import_csv),
                        onClick = {
                            importCsv.launch(
                                arrayOf("text/*", "application/csv", "application/vnd.ms-excel")
                            )
                        },
                        summary = stringResource(R.string.import_csv_summary),
                        icon = { Icon(painterResource(R.drawable.ic_upload), null) },
                    )
                }
            }
        }
        item {
            PvotListGroup {
                item {
                    PvotPreferenceRow(
                        stringResource(R.string.about),
                        onClick = nav::about,
                        icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    )
                }
            }
        }
    }

    PvotDialog(
        visible = disclosure,
        onDismissRequest = { disclosure = false },
        title = stringResource(R.string.notification_access_title),
        message = stringResource(R.string.notification_access_message),
    ) {
        PvotDialogAction(
            stringResource(R.string.open_settings),
            onClick = {
                disclosure = false
                vm.setCaptureNotifications(true)
                askNotifications()
                context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            style = PvotDialogActionStyle.Primary,
        )
        PvotDialogAction(stringResource(R.string.cancel), onClick = { disclosure = false })
    }
    PickerSheet(
        picker == 1,
        stringResource(R.string.base_currency),
        (listOf(s.baseCurrency) + CURRENCIES).distinct().map {
            Choice(it, it, Formats.currencyName(it))
        },
        onPick = vm::setBaseCurrency,
        onDismiss = { picker = 0 },
    )
    PickerSheet(
        picker == 2,
        stringResource(R.string.month_start),
        (1..28).map { Choice(it, stringResource(R.string.month_start_value, it)) },
        onPick = vm::setMonthStartDay,
        onDismiss = { picker = 0 },
    )
    PassphraseDialog(
        visible = passphraseFor != null,
        onDismiss = { passphraseFor = null },
        onDone = { pass ->
            val uri = restoreUri
            if (uri != null) vm.restore(uri, pass) else vm.setPassphrase(pass)
            restoreUri = null
            passphraseFor = null
        },
        requireLength = restoreUri == null,
    )
    PvotDialog(
        visible = confirmRestore,
        onDismissRequest = {
            confirmRestore = false
            restoreUri = null
        },
        title = stringResource(R.string.restore_title),
        message = stringResource(R.string.restore_message),
        destructive = true,
    ) {
        PvotDialogAction(
            stringResource(R.string.restore),
            onClick = {
                confirmRestore = false
                passphraseFor = restoreUri
            },
            style = PvotDialogActionStyle.Destructive,
        )
        PvotDialogAction(
            stringResource(R.string.cancel),
            onClick = {
                confirmRestore = false
                restoreUri = null
            },
        )
    }
}

@Composable
private fun PassphraseDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onDone: (CharArray) -> Unit,
    requireLength: Boolean,
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (!visible) text = "" }
    val ok = if (requireLength) text.length >= 8 else text.isNotEmpty()
    PvotDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.passphrase),
        content = {
            PvotTextField(
                text,
                { text = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText =
                    if (requireLength) stringResource(R.string.passphrase_short) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        PvotDialogAction(
            stringResource(R.string.done),
            onClick = { onDone(text.toCharArray()) },
            style = PvotDialogActionStyle.Primary,
            enabled = ok,
        )
        PvotDialogAction(stringResource(R.string.cancel), onClick = onDismiss)
    }
}
