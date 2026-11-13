// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val baseCurrency: String,
    val monthStartDay: Int = 1,
    val lock: Boolean = false,
    val hideAmounts: Boolean = false,
    val secureScreen: Boolean = false,
    val captureNotifications: Boolean = false,
    val captureSms: Boolean = false,
    val captureMessages: Boolean = false,
    val backupUri: String? = null,
    val hasPassphrase: Boolean = false,
    val lastBackupAt: Long = 0,
    val onboarded: Boolean = false,
)

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val keystore = Keystore("ledger_backup")

    val settings: Flow<Settings> = context.store.data.map { it.toSettings() }

    suspend fun now(): Settings = settings.first()

    private fun Preferences.toSettings() =
        Settings(
            baseCurrency = this[BASE] ?: defaultCurrency(),
            monthStartDay = this[START_DAY] ?: 1,
            lock = this[LOCK] ?: false,
            hideAmounts = this[HIDE] ?: false,
            secureScreen = this[SECURE] ?: false,
            captureNotifications = this[NOTIFICATIONS] ?: false,
            captureSms = this[SMS] ?: false,
            captureMessages = this[MESSAGES] ?: false,
            backupUri = this[BACKUP_URI],
            hasPassphrase = this[PASSPHRASE] != null,
            lastBackupAt = this[LAST_BACKUP] ?: 0,
            onboarded = this[ONBOARDED] ?: false,
        )

    suspend fun setBaseCurrency(code: String) = context.store.edit { it[BASE] = code }

    suspend fun setMonthStartDay(day: Int) =
        context.store.edit { it[START_DAY] = day.coerceIn(1, 28) }

    suspend fun setLock(on: Boolean) = context.store.edit { it[LOCK] = on }

    suspend fun setHideAmounts(on: Boolean) = context.store.edit { it[HIDE] = on }

    suspend fun setSecureScreen(on: Boolean) = context.store.edit { it[SECURE] = on }

    suspend fun setCaptureNotifications(on: Boolean) = context.store.edit { it[NOTIFICATIONS] = on }

    suspend fun setCaptureSms(on: Boolean) = context.store.edit { it[SMS] = on }

    suspend fun setCaptureMessages(on: Boolean) = context.store.edit { it[MESSAGES] = on }

    suspend fun setBackupUri(uri: String?) =
        context.store.edit { if (uri == null) it.remove(BACKUP_URI) else it[BACKUP_URI] = uri }

    suspend fun setLastBackupAt(at: Long) = context.store.edit { it[LAST_BACKUP] = at }

    suspend fun setOnboarded() = context.store.edit { it[ONBOARDED] = true }

    suspend fun setPassphrase(passphrase: CharArray) {
        val wrapped = keystore.wrap(String(passphrase).toByteArray())
        context.store.edit { it[PASSPHRASE] = Base64.encodeToString(wrapped, Base64.NO_WRAP) }
    }

    suspend fun passphrase(): CharArray? =
        context.store.data.first()[PASSPHRASE]?.let {
            keystore.unwrap(Base64.decode(it, Base64.NO_WRAP)).decodeToString().toCharArray()
        }

    suspend fun smsCursor(): Long = context.store.data.first()[SMS_CURSOR] ?: 0

    suspend fun setSmsCursor(at: Long) = context.store.edit { it[SMS_CURSOR] = at }

    private companion object {
        val BASE = stringPreferencesKey("base_currency")
        val START_DAY = intPreferencesKey("month_start_day")
        val LOCK = booleanPreferencesKey("lock")
        val HIDE = booleanPreferencesKey("hide_amounts")
        val SECURE = booleanPreferencesKey("secure_screen")
        val NOTIFICATIONS = booleanPreferencesKey("capture_notifications")
        val SMS = booleanPreferencesKey("capture_sms")
        val MESSAGES = booleanPreferencesKey("capture_messages")
        val BACKUP_URI = stringPreferencesKey("backup_uri")
        val PASSPHRASE = stringPreferencesKey("backup_passphrase")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val SMS_CURSOR = longPreferencesKey("sms_cursor")

        fun defaultCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }
                .getOrDefault("INR")
    }
}
