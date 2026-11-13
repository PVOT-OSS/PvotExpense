// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense

import android.app.Application
import org.prauga.expense.work.DailyWorker
import org.prauga.expense.work.Notifications

class LedgerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        DailyWorker.schedule(this)
        container.start()
    }
}
