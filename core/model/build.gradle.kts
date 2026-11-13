// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.ledger.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "org.prauga.expense.core.model" }

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.room.common)
}
