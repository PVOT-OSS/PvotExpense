// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins { alias(libs.plugins.ledger.android.library) }

android { namespace = "org.prauga.expense.core.capture" }

dependencies { api(project(":core:model")) }
