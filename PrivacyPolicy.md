<!--
SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
SPDX-License-Identifier: Apache-2.0
-->

# Privacy Policy — Ledger

_Effective 15 November 2026_

Ledger (package `org.prauga.expense`) is an expense manager published by Saalim Quadri for PVOT-OSS.
This policy explains what the app does with your data.

**In short: the app doesn't collect, send, share or sell any personal data. It has no accounts, no
ads, no analytics and no tracking, and it has no permission to use the internet. Everything you
record stays on your phone, in an encrypted database.**

## What the app keeps on your device

- **Your money records**: accounts (name, type, currency, opening balance, and optionally the last
  four digits of a bank account or card, a credit limit and statement and due days), categories,
  transactions (amount, currency, date, account, category, payee, note), budgets, recurring payments
  and bills, exchange rates you enter, and the balances you tell the app your bank reports.
- **Payments read by the app** (see below): besides the transaction itself, the text of the SMS or
  notification it was read from, so you can check it, and the account digits and payment reference
  it mentioned, so the same payment isn't recorded twice.
- **What the app learns from you**: which category and account you choose for a payee, so the next
  payment from that payee is filed the same way.
- **Your settings**: main currency, the day your month starts, capture, lock and privacy options,
  the backup folder you picked and when the last backup ran.

The records are kept in a database encrypted with SQLCipher. Its key is created on your phone and
protected by the Android Keystore; it never leaves the phone. Everything is deleted when you clear
the app's storage or uninstall it.

Ledger opts out of Android's device backup and device-to-device transfer, so none of this is copied
into Google's or your phone maker's backup. Use the app's own backups (below) to keep a copy.

## Payments the app reads, only if you turn it on

Both are off until you turn them on in Settings, and you can turn them off at any time.

- **Payment notifications.** With Android's notification access, the app reads the title and text of
  notifications from payment and banking apps (such as Google Pay, PhonePe, Paytm, BHIM and banks'
  own apps) to find payments. Notifications from every other app are ignored and not stored.
- **Bank SMS** (the version from F-Droid). With the SMS permissions, the app looks at new SMS as they
  arrive and, when you ask it to, at the last 90 days of your inbox. It keeps only messages from
  banks' sender names (such as `AX-HDFCBK`) that describe a debit or credit; messages from people
  and every other SMS are ignored and not stored.
- **Bank SMS from Messages** (the version from Google Play). This version can't read SMS. If you
  install the PVOT Messages app and allow it, Messages gives Ledger only the bank messages that
  describe payments.

Payments found this way wait in the app's inbox until you confirm or dismiss them.

## Text you share

When you share text from another app to Ledger (for example a payment confirmation), the app reads it
to find a payment and puts it in the inbox. Nothing is shared from Ledger unless you choose to.

## Backups and exports

- **Backups** are written to a folder you choose, protected with a passphrase you set (AES-256-GCM,
  with a key derived from the passphrase). The passphrase is kept on your phone, protected by the
  Android Keystore, so automatic backups can run. If the folder you choose belongs to a cloud
  service (such as Google Drive), that service stores the encrypted file under its own terms; without
  the passphrase the file can't be read.
- **CSV exports** are plain, unencrypted text files with your transactions, saved where you choose.
  Anyone or any app with access to that file can read it.
- **Imports** (a backup or a CSV file) are read only when you pick the file.

## Notifications, widget and app lock

- The app can notify you about bills that are due and about payments it found. Depending on your
  lock screen settings, these notifications may show an amount and a payee on your lock screen.
- The home screen widget shows how much you can spend today. It shows ••• instead when the app
  lock or "Hide amounts" is on.
- The app lock uses Android's own fingerprint, face or screen lock check. The app never sees your
  biometric data or your screen lock.

## Permissions

- `POST_NOTIFICATIONS`: to tell you about bills and found payments. You can refuse.
- `USE_BIOMETRIC`: for the app lock, if you turn it on.
- `READ_SMS`, `RECEIVE_SMS` (F-Droid version only): to read bank SMS, if you turn it on.
- `org.prauga.messages.permission.READ_TRANSACTION_SMS` (Google Play version only): to receive bank
  SMS from the Messages app, if you turn it on.
- Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`): granted by you in Android's settings,
  only if you turn on payment notifications.
- `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`: added by Android's WorkManager
  library, which posts recurring payments, reminds you of bills and runs backups twice a day. They
  don't give the app access to any of your data.
- `org.prauga.expense.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: added by the AndroidX library to
  keep the app's internal broadcasts private; it gives the app no access to your data.

The app does not have the `INTERNET` permission.

## Children

The app is not directed at children and collects no data from anyone, children included.

## Changes

If the app starts handling data differently, this policy will be updated before that version is
released, with a new effective date at the top.

## Contact

Questions about this policy: danascape@gmail.com, or open an issue at
https://github.com/PVOT-OSS/PvotExpense/issues.
