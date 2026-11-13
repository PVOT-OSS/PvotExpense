// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.ledger.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android { namespace = "org.prauga.expense.core.data" }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    api(project(":core:model"))
    api(project(":core:ledger"))
    api(project(":core:capture"))
    api(libs.room.runtime)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    ksp(libs.room.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
}
