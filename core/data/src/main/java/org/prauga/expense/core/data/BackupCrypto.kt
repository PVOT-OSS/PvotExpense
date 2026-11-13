// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.expense.core.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPassphraseException : Exception()

/**
 * Backup files: "LEDGER" + version byte + PBKDF2 iterations + salt + IV + AES-256-GCM of gzipped
 * JSON. Readable anywhere with the passphrase; useless without it.
 */
object BackupCrypto {
    private val MAGIC = "LEDGER".toByteArray()
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 310_000

    fun encrypt(json: String, passphrase: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            derive(passphrase, salt, iterations),
            GCMParameterSpec(128, iv),
        )
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(gzip(json.toByteArray()))
        return ByteBuffer.allocate(MAGIC.size + 1 + 4 + salt.size + iv.size + body.size)
            .put(MAGIC)
            .put(VERSION)
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .put(body)
            .array()
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): String {
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        require(magic.contentEquals(MAGIC) && buf.get() == VERSION) { "Not a Ledger backup" }
        val iterations = buf.getInt()
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            derive(passphrase, salt, iterations),
            GCMParameterSpec(128, iv),
        )
        cipher.updateAAD(MAGIC)
        val plain =
            try {
                cipher.doFinal(body)
            } catch (_: AEADBadTagException) {
                throw WrongPassphraseException()
            }
        return GZIPInputStream(plain.inputStream()).readBytes().decodeToString()
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        val bytes =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
