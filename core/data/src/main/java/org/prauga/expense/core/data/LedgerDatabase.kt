// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.prauga.expense.core.model.Account
import org.prauga.expense.core.model.Budget
import org.prauga.expense.core.model.Category
import org.prauga.expense.core.model.Checkpoint
import org.prauga.expense.core.model.Rate
import org.prauga.expense.core.model.Rule
import org.prauga.expense.core.model.Schedule
import org.prauga.expense.core.model.Transaction

@Database(
    entities =
        [
            Account::class,
            Category::class,
            Transaction::class,
            Budget::class,
            Schedule::class,
            Rule::class,
            Checkpoint::class,
            Rate::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        /** Always encrypted: SQLCipher, with a random key kept wrapped by the Android Keystore. */
        fun open(context: Context): LedgerDatabase {
            System.loadLibrary("sqlcipher")
            val key = DatabaseKey(context).get()
            return Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .build()
        }
    }
}
