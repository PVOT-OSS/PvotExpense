// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.ui

/** Where a screen can go next. */
interface Nav {
    fun back()

    fun transaction(id: String?)

    fun inbox()

    fun accounts()

    fun account(id: String)

    fun editAccount(id: String?)

    fun categories()

    fun schedule(id: String?)

    fun settings()

    fun rates()

    fun about()
}
