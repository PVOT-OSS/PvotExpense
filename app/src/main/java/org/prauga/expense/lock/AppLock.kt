// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.lock

import android.app.Activity
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

object AppLock {
    /** Asks for a fingerprint, face or the screen lock; [onUnlocked] runs on success. */
    fun prompt(activity: Activity, title: String, onUnlocked: () -> Unit) {
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        }
        builder
            .build()
            .authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) = onUnlocked()
                },
            )
    }
}
