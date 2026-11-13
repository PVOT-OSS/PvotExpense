// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class AccountType(val liability: Boolean) {
    CASH(false),
    BANK(false),
    WALLET(false),
    CARD(true),
    LOAN(true),
    PERSON(false),
}

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingMinor: Long = 0,
    val last4: String? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    val limitMinor: Long? = null,
    val archived: Boolean = false,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
) {
    val spendable: Boolean
        get() = type == AccountType.CASH || type == AccountType.BANK || type == AccountType.WALLET
}

@Serializable
enum class CategoryKind {
    EXPENSE,
    INCOME,
}

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val kind: CategoryKind,
    val color: Int = 0,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
enum class TxType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

@Serializable
enum class TxStatus {
    PENDING,
    CONFIRMED,
}

@Serializable
enum class TxSource {
    MANUAL,
    QUICK_ADD,
    SMS,
    NOTIFICATION,
    SHARE,
    RECURRING,
    IMPORT,

    /** Brings a balance in line with the bank; never counts as income or spending. */
    ADJUSTMENT,
}

/**
 * [amountMinor] is always positive, in the account's currency. A transfer moves it out of
 * [accountId] and [toAmountMinor] (in the target's currency) into [toAccountId].
 */
@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("occurredAt"), Index("status"), Index("accountId"), Index("reference")],
)
data class Transaction(
    @PrimaryKey val id: String = newId(),
    val type: TxType,
    val status: TxStatus = TxStatus.CONFIRMED,
    val source: TxSource = TxSource.MANUAL,
    val occurredAt: Long,
    val accountId: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String? = null,
    val toAccountId: String? = null,
    val toAmountMinor: Long? = null,
    val payee: String = "",
    val note: String = "",
    val reference: String? = null,
    val accountHint: String? = null,
    val raw: String? = null,
    val scheduleId: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
) {
    val money: Money
        get() = Money(amountMinor, currency)
}

@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = newId(),
    val categoryId: String,
    val limitMinor: Long,
    val rollover: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
enum class Frequency {
    WEEKLY,
    MONTHLY,
    YEARLY,
}

/** A repeating transaction; [nextDue] is an epoch day. */
@Serializable
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey val id: String = newId(),
    val type: TxType,
    val accountId: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String? = null,
    val toAccountId: String? = null,
    val payee: String,
    val note: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val nextDue: Long,
    val remindDays: Int = 2,
    val autoConfirm: Boolean = false,
    val active: Boolean = true,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/** Learned from the user's choices: text matching [pattern] goes to [categoryId]. */
@Serializable
@Entity(tableName = "rules", indices = [Index("pattern")])
data class Rule(
    @PrimaryKey val id: String = newId(),
    val pattern: String,
    val categoryId: String? = null,
    val accountId: String? = null,
    val hits: Int = 1,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/** A balance the bank reported, to reconcile against. */
@Serializable
@Entity(tableName = "checkpoints", indices = [Index("accountId")])
data class Checkpoint(
    @PrimaryKey val id: String = newId(),
    val accountId: String,
    val at: Long,
    val balanceMinor: Long,
    val source: TxSource = TxSource.MANUAL,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/** 1 unit of [currency] is worth [perBase] units of the base currency. */
@Serializable
@Entity(tableName = "rates")
data class Rate(@PrimaryKey val currency: String, val perBase: String, val updatedAt: Long = 0)
