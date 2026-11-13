// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.ledger.android.application)
    alias(libs.plugins.ledger.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.prauga.expense"

    defaultConfig {
        applicationId = "org.prauga.expense"
        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions += "store"
    productFlavors {
        create("fdroid") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    buildTypes {
        debug { applicationIdSuffix = ".dev" }
        release { optimization { enable = true } }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.pvot.design.system)
    implementation(libs.pvot.core.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
