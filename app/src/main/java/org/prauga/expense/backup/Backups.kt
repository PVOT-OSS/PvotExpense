// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.prauga.expense.R
import org.prauga.expense.core.data.BackupCrypto
import org.prauga.expense.core.data.Csv
import org.prauga.expense.core.data.LedgerRepository
import org.prauga.expense.core.data.SettingsStore
import org.prauga.expense.core.data.Snapshot
import org.prauga.expense.core.data.WrongPassphraseException
import org.prauga.expense.ui.Message

/** Encrypted backups into a folder the user picked (any storage provider), plus CSV in and out. */
class Backups(
    private val context: Context,
    private val repository: LedgerRepository,
    private val settings: SettingsStore,
) {
    private val resolver = context.contentResolver

    suspend fun backUp(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val s = settings.now()
                val tree =
                    s.backupUri?.toUri() ?: error(context.getString(R.string.backup_needs_setup))
                val passphrase =
                    settings.passphrase() ?: error(context.getString(R.string.backup_needs_setup))
                val bytes = BackupCrypto.encrypt(repository.snapshot().toJson(), passphrase)
                passphrase.fill(' ')
                val parent =
                    DocumentsContract.buildDocumentUriUsingTree(
                        tree,
                        DocumentsContract.getTreeDocumentId(tree),
                    )
                val name =
                    "ledger-" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) +
                        ".ledger"
                val doc =
                    DocumentsContract.createDocument(
                        resolver,
                        parent,
                        "application/octet-stream",
                        name,
                    ) ?: error("createDocument")
                resolver.openOutputStream(doc, "wt")!!.use { it.write(bytes) }
                prune(tree)
                settings.setLastBackupAt(System.currentTimeMillis())
                Unit
            }
        }

    /** Keeps the newest ten backups. */
    private fun prune(tree: Uri) {
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
        val found = mutableListOf<Pair<String, String>>()
        resolver
            .query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )
            ?.use { c -> while (c.moveToNext()) found += c.getString(0) to c.getString(1) }
        found
            .filter { it.second.startsWith("ledger-") && it.second.endsWith(".ledger") }
            .sortedByDescending { it.second }
            .drop(10)
            .forEach {
                runCatching {
                    DocumentsContract.deleteDocument(
                        resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, it.first),
                    )
                }
            }
    }

    suspend fun restore(uri: String, passphrase: CharArray): Message =
        withContext(Dispatchers.IO) {
            try {
                val bytes = resolver.openInputStream(uri.toUri())!!.use { it.readBytes() }
                repository.replaceAll(Snapshot.fromJson(BackupCrypto.decrypt(bytes, passphrase)))
                Message(R.string.restore_done)
            } catch (_: WrongPassphraseException) {
                Message(R.string.restore_wrong_passphrase)
            } catch (e: Exception) {
                Message(R.string.restore_failed, listOf(e.message.orEmpty()))
            } finally {
                passphrase.fill(' ')
            }
        }

    suspend fun exportCsv(uri: String): Message =
        withContext(Dispatchers.IO) {
            val d = repository.data.first()
            val text = Csv.export(d.transactions, d.accounts, d.categories, ZoneId.systemDefault())
            resolver.openOutputStream(uri.toUri(), "wt")!!.use { it.write(text.toByteArray()) }
            Message(R.string.exported)
        }

    suspend fun importCsv(uri: String): Message =
        withContext(Dispatchers.IO) {
            val text =
                resolver.openInputStream(uri.toUri())!!.use { it.readBytes().decodeToString() }
            val rows = Csv.parse(text)
            if (rows.isEmpty()) Message(R.string.import_nothing)
            else
                Message(
                    R.string.imported,
                    listOf(
                        repository.import(rows, settings.now().baseCurrency, ZoneId.systemDefault())
                    ),
                )
        }
}
