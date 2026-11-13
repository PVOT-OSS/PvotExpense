// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.prauga.expense.lock.AppLock
import org.prauga.expense.ui.AppViewModel
import org.prauga.expense.ui.Launch
import org.prauga.expense.ui.LedgerApp
import org.prauga.expense.ui.LockScreen

class MainActivity : PvotBaseActivity() {
    private val container by lazy { (application as LedgerApplication).container }
    private val vm: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(container) as T
        }
    }
    private val launch = MutableStateFlow(Launch.NONE)
    private var locked by mutableStateOf(true)
    private var lockEnabled = false
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settings.settings.collect { s ->
                    lockEnabled = s.lock
                    if (!s.lock) locked = false
                    if (s.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setPvotContent {
            PvotAppTheme {
                if (lockEnabled && locked)
                    LockScreen(
                        onUnlock = {
                            AppLock.prompt(this, getString(R.string.unlock_title)) {
                                locked = false
                            }
                        }
                    )
                else LedgerApp(vm, launch, onLaunchHandled = { launch.value = Launch.NONE })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_MILLIS)
            locked = true
        container.scope.launch { runCatching { container.capture.pullFromMessages() } }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_ADD -> launch.value = Launch.QUICK_ADD
            ACTION_INBOX -> launch.value = Launch.INBOX
            ACTION_PLAN -> launch.value = Launch.PLAN
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                vm.shared(text)
                launch.value = Launch.INBOX
            }
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "org.prauga.expense.QUICK_ADD"
        const val ACTION_INBOX = "org.prauga.expense.INBOX"
        const val ACTION_PLAN = "org.prauga.expense.PLAN"
        private const val RELOCK_MILLIS = 30_000L
    }
}
